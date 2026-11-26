import React, { useEffect, useMemo, useState } from 'react';
import { CalendarClock, Clock, Globe, Pencil, Pin, Plus, Trash2, X } from 'lucide-react';
import { ExamCountdownTarget } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { StudyDateUtils } from '../utils/studyPlannerEngine';

interface ExamCountdownsModalProps {
  isOpen: boolean;
  countdowns: ExamCountdownTarget[];
  onSaveCountdowns: (next: ExamCountdownTarget[]) => void;
  onClose: () => void;
}

export function getDaysRemaining(targetDateIso: string): number {
  return StudyDateUtils.computeDaysRemaining(targetDateIso) ?? 0;
}

export const ExamCountdownsModal: React.FC<ExamCountdownsModalProps> = ({
  isOpen,
  countdowns,
  onSaveCountdowns,
  onClose,
}) => {
  const { colors } = useTheme();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [dateIso, setDateIso] = useState(() => StudyDateUtils.addDaysToDateString(Date.now(), 30));
  const [timeStr, setTimeStr] = useState('00:00');
  const [timezoneMode, setTimezoneMode] = useState<'LOCAL' | 'GMT'>('LOCAL');
  const [examType, setExamType] = useState('');
  const [isPinned, setIsPinned] = useState(true);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (!isOpen) return;
    setNowMs(Date.now());
    const hasTimed = countdowns.some((cd) => StudyDateUtils.hasExplicitCountdownTime(cd));
    if (!hasTimed) return;
    const timer = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [isOpen, countdowns]);

  const localTzLabel = useMemo(() => StudyDateUtils.getLocalTimezoneLabel(nowMs), [nowMs]);

  const effectiveTime = timeStr.trim() || '00:00';
  const previewUtcMs = useMemo(
    () => StudyDateUtils.parseDateTimeMillis(dateIso, effectiveTime, timezoneMode === 'GMT'),
    [dateIso, effectiveTime, timezoneMode]
  );

  const sortedCountdowns = useMemo(() => {
    return [...countdowns].sort((a, b) => {
      const ra = StudyDateUtils.computeCountdownRemainingMillis(a, nowMs) ?? Number.MAX_SAFE_INTEGER;
      const rb = StudyDateUtils.computeCountdownRemainingMillis(b, nowMs) ?? Number.MAX_SAFE_INTEGER;
      return ra - rb;
    });
  }, [countdowns, nowMs]);

  if (!isOpen) return null;

  const resetForm = () => {
    setEditingId(null);
    setTitle('');
    setDateIso(StudyDateUtils.addDaysToDateString(Date.now(), 30));
    setTimeStr('00:00');
    setTimezoneMode('LOCAL');
    setExamType('');
    setIsPinned(true);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanTitle = title.trim();
    if (!cleanTitle || !dateIso) return;
    const cleanTime = StudyDateUtils.normalizeTimeHmOrBlank(timeStr.trim() || '00:00') || '00:00';
    const targetUtcMillis =
      StudyDateUtils.parseDateTimeMillis(dateIso, cleanTime, timezoneMode === 'GMT') ?? undefined;

    if (editingId) {
      onSaveCountdowns(
        countdowns.map((cd) =>
          cd.id === editingId
            ? {
                ...cd,
                title: cleanTitle,
                targetDateIso: dateIso,
                targetTime: cleanTime,
                timezoneMode,
                targetUtcMillis,
                examType: examType.trim() || 'General Assessment',
                isPinned,
              }
            : cd
        )
      );
    } else {
      const next: ExamCountdownTarget = {
        id: `cd-${Date.now()}`,
        title: cleanTitle,
        targetDateIso: dateIso,
        targetTime: cleanTime,
        timezoneMode,
        targetUtcMillis,
        examType: examType.trim() || 'General Assessment',
        isPinned,
        color: colors.teal,
      };
      onSaveCountdowns([next, ...countdowns]);
    }
    resetForm();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[88vh]"
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
                Canonical UTC countdowns auto-displayed in your local timezone ({localTzLabel})
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg" style={{ color: colors.muted }} aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 overflow-y-auto space-y-4">
          <form
            onSubmit={handleSave}
            className="p-3.5 rounded-xl space-y-2.5 text-xs"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
          >
            <div className="flex items-center justify-between">
              <span className="font-bold text-xs" style={{ color: colors.inkBold }}>
                {editingId ? 'Edit Exam Target' : 'Add Exam Target'}
              </span>
              {editingId && (
                <button
                  type="button"
                  onClick={resetForm}
                  className="text-[0.6875rem] font-semibold underline"
                  style={{ color: colors.muted }}
                >
                  Cancel Edit
                </button>
              )}
            </div>

            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Target (e.g., Term-End Exam / Finals)"
              className="w-full px-3 py-2 rounded-lg focus:outline-none"
              style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            />

            {/* [DEPRECATED 2026-11-25] Reason: Quick date offset chips (+7d/+15d/+30d/+60d/+90d) removed per user request ("no quick thing in it"). Restore if: Quick preset date shortcuts are requested again. */}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <label className="block space-y-1">
                <span className="text-[0.6875rem] font-semibold" style={{ color: colors.muted }}>
                  Date
                </span>
                <input
                  type="date"
                  required
                  value={dateIso}
                  onChange={(e) => setDateIso(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg focus:outline-none"
                  style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                />
              </label>

              <label className="block space-y-1">
                <span className="text-[0.6875rem] font-semibold flex items-center gap-1" style={{ color: colors.muted }}>
                  <Clock className="w-3 h-3" />
                  Time (24h, default 00:00)
                </span>
                <input
                  type="time"
                  required
                  value={timeStr}
                  onChange={(e) => setTimeStr(e.target.value || '00:00')}
                  className="w-full px-3 py-2 rounded-lg focus:outline-none"
                  style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                />
              </label>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <span className="text-[0.6875rem] font-semibold flex items-center gap-1" style={{ color: colors.muted }}>
                <Globe className="w-3 h-3" />
                Zone:
              </span>
              <button
                type="button"
                onClick={() => setTimezoneMode('LOCAL')}
                className="px-3 py-1.5 rounded-lg text-[0.6875rem] font-bold border transition"
                style={
                  timezoneMode === 'LOCAL'
                    ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                    : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                }
              >
                Local ({localTzLabel})
              </button>
              <button
                type="button"
                onClick={() => setTimezoneMode('GMT')}
                className="px-3 py-1.5 rounded-lg text-[0.6875rem] font-bold border transition"
                style={
                  timezoneMode === 'GMT'
                    ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                    : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                }
              >
                GMT (UTC)
              </button>
            </div>

            {previewUtcMs !== null && (
              <div className="text-[0.6875rem] font-medium" style={{ color: colors.tealDark }}>
                Targets {StudyDateUtils.formatHumanDateTimeLocal(previewUtcMs)}
                {timezoneMode === 'GMT' &&
                localTzLabel.toUpperCase() !== 'GMT' &&
                localTzLabel.toUpperCase() !== 'UTC'
                  ? ` (${effectiveTime} GMT)`
                  : ''}
              </div>
            )}

            <div className="flex flex-wrap items-center gap-2">
              <input
                type="text"
                value={examType}
                onChange={(e) => setExamType(e.target.value)}
                placeholder="Course / Subject Focus (Optional)"
                className="flex-1 min-w-[180px] px-3 py-2 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
              <label className="inline-flex items-center gap-1.5 px-1 cursor-pointer select-none whitespace-nowrap">
                <input
                  type="checkbox"
                  checked={isPinned}
                  onChange={(e) => setIsPinned(e.target.checked)}
                />
                <span className="text-[0.6875rem] font-semibold" style={{ color: colors.inkBold }}>
                  Pin
                </span>
              </label>
              <button
                type="submit"
                className="flex items-center justify-center gap-1 px-4 py-2 rounded-lg font-bold whitespace-nowrap"
                style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
              >
                <Plus className="w-3.5 h-3.5" />
                {editingId ? 'Update' : 'Add'}
              </button>
            </div>
          </form>

          <div className="space-y-2">
            {sortedCountdowns.length === 0 ? (
              <div
                className="p-5 rounded-xl text-center text-xs"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.muted }}
              >
                No exam targets yet. Add one above to track remaining days and hours.
              </div>
            ) : (
              sortedCountdowns.map((cd) => {
                const days = StudyDateUtils.computeCountdownDaysRemaining(cd, nowMs);
                const remMs = StudyDateUtils.computeCountdownRemainingMillis(cd, nowMs);
                const isPast = remMs !== null && remMs < 0;
                const isUrgent = !isPast && days !== null && days <= 7;
                return (
                  <div
                    key={cd.id}
                    className="p-3 rounded-xl space-y-1.5"
                    style={{
                      backgroundColor: colors.surfaceVariant,
                      border: `1px solid ${cd.isPinned ? colors.teal : colors.border}`,
                    }}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="text-xs font-bold line-clamp-2 min-w-0 flex-1" style={{ color: colors.inkBold }}>
                        {cd.title}
                      </div>
                      <span
                        className="px-2.5 py-0.5 rounded-full text-[0.6875rem] font-black whitespace-nowrap flex-shrink-0"
                        style={{
                          backgroundColor: isUrgent ? colors.redLight : colors.tealLight,
                          color: isPast ? colors.muted : isUrgent ? colors.red : colors.tealDark,
                        }}
                      >
                        {StudyDateUtils.formatCountdownDetailed(cd, nowMs)}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <div className="text-[0.6875rem] break-words" style={{ color: colors.ink }}>
                          {StudyDateUtils.formatCountdownSchedule(cd)}
                        </div>
                        {cd.examType && (
                          <div className="text-[0.625rem] line-clamp-2" style={{ color: colors.muted }}>
                            {cd.examType}
                          </div>
                        )}
                      </div>
                      <div className="flex items-center gap-1 flex-shrink-0">
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
                          onClick={() => {
                            setEditingId(cd.id);
                            setTitle(cd.title);
                            setDateIso(cd.targetDateIso);
                            setTimeStr(cd.targetTime || '00:00');
                            setTimezoneMode(cd.timezoneMode === 'GMT' ? 'GMT' : 'LOCAL');
                            setExamType(cd.examType || '');
                            setIsPinned(cd.isPinned);
                          }}
                          title="Edit countdown"
                          className="p-1.5 rounded-lg border"
                          style={{
                            borderColor: colors.border,
                            backgroundColor: colors.surface,
                            color: colors.inkBold,
                          }}
                        >
                          <Pencil className="w-3.5 h-3.5" />
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
