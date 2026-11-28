import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Bell,
  BellOff,
  CalendarClock,
  Clock,
  Globe,
  Pencil,
  Pin,
  Plus,
  Trash2,
  Vibrate,
  Volume2,
  X,
} from 'lucide-react';
import { ExamCountdownTarget } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { StudyDateUtils } from '../utils/studyPlannerEngine';
import {
  clampLeadMinutes,
  computeEarlyAlertTriggerMillis,
  DEFAULT_ALERT_LEAD_MINUTES,
  formatLeadDuration,
  inspectWebAlertReadiness,
  requestBrowserNotificationPermission,
  testWebCountdownAlert,
} from '../utils/countdownAlertEngine';

interface ExamCountdownsModalProps {
  isOpen: boolean;
  countdowns: ExamCountdownTarget[];
  defaultLeadMinutes?: number;
  onSaveDefaultLeadMinutes?: (minutes: number) => void;
  onSaveCountdowns: (next: ExamCountdownTarget[]) => void;
  onClose: () => void;
}

export function getDaysRemaining(targetDateIso: string): number {
  return StudyDateUtils.computeDaysRemaining(targetDateIso) ?? 0;
}

export const ExamCountdownsModal: React.FC<ExamCountdownsModalProps> = ({
  isOpen,
  countdowns,
  defaultLeadMinutes = DEFAULT_ALERT_LEAD_MINUTES,
  onSaveDefaultLeadMinutes,
  onSaveCountdowns,
  onClose,
}) => {
  const { colors } = useTheme();
  const safeDefaultLead = clampLeadMinutes(defaultLeadMinutes);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [dateIso, setDateIso] = useState(() => StudyDateUtils.addDaysToDateString(Date.now(), 30));
  const [timeStr, setTimeStr] = useState('00:00');
  const [timezoneMode, setTimezoneMode] = useState<'LOCAL' | 'GMT'>('LOCAL');
  const [examType, setExamType] = useState('');
  const [isPinned, setIsPinned] = useState(true);

  const [alertEnabled, setAlertEnabled] = useState(true);
  const [leadHoursStr, setLeadHoursStr] = useState(() => String(Math.floor(safeDefaultLead / 60)));
  const [leadMinsStr, setLeadMinsStr] = useState(() => String(safeDefaultLead % 60));
  const [alertMessage, setAlertMessage] = useState('');
  const [vibrateOnAlert, setVibrateOnAlert] = useState(true);
  const [alertSoundEnabled, setAlertSoundEnabled] = useState(true);
  const [alertAtZero, setAlertAtZero] = useState(true);
  const [readiness, setReadiness] = useState(() => inspectWebAlertReadiness());
  const [testStatus, setTestStatus] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (!isOpen) return;
    setNowMs(Date.now());
    setReadiness(inspectWebAlertReadiness());
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

  const parsedLeadHours = Math.max(0, Math.min(720, parseInt(leadHoursStr || '0', 10) || 0));
  const parsedLeadMins = Math.max(0, Math.min(59, parseInt(leadMinsStr || '0', 10) || 0));
  const rawTotalLead = parsedLeadHours * 60 + parsedLeadMins;
  const isLeadInvalid = alertEnabled && rawTotalLead < 1;
  const computedLeadMinutes = clampLeadMinutes(rawTotalLead > 0 ? rawTotalLead : safeDefaultLead);

  const previewEarlyTriggerMs = useMemo(() => {
    if (!alertEnabled || previewUtcMs === null || isLeadInvalid) return null;
    return previewUtcMs - computedLeadMinutes * 60_000;
  }, [alertEnabled, previewUtcMs, isLeadInvalid, computedLeadMinutes]);

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
    setAlertEnabled(true);
    setLeadHoursStr(String(Math.floor(safeDefaultLead / 60)));
    setLeadMinsStr(String(safeDefaultLead % 60));
    setAlertMessage('');
    setVibrateOnAlert(true);
    setAlertSoundEnabled(true);
    setAlertAtZero(true);
    setTestStatus(null);
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanTitle = title.trim();
    if (!cleanTitle || !dateIso || isLeadInvalid) return;
    const cleanTime = StudyDateUtils.normalizeTimeHmOrBlank(timeStr.trim() || '00:00') || '00:00';
    const targetUtcMillis =
      StudyDateUtils.parseDateTimeMillis(dateIso, cleanTime, timezoneMode === 'GMT') ?? undefined;
    const cleanMsg = alertMessage.trim();

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
                alertEnabled,
                alertLeadMinutes: computedLeadMinutes,
                alertMessage: cleanMsg,
                vibrateOnAlert,
                alertSoundEnabled,
                alertAtZero,
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
        alertEnabled,
        alertLeadMinutes: computedLeadMinutes,
        alertMessage: cleanMsg,
        vibrateOnAlert,
        alertSoundEnabled,
        alertAtZero,
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

            <div className="pt-2 space-y-2.5" style={{ borderTop: `1px solid ${colors.border}` }}>
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  {alertEnabled ? (
                    <Bell className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
                  ) : (
                    <BellOff className="w-4 h-4 flex-shrink-0" style={{ color: colors.muted }} />
                  )}
                  <div>
                    <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                      Early Alert & Vibration
                    </div>
                    <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                      Default: {formatLeadDuration(safeDefaultLead)} before target
                    </div>
                  </div>
                </div>
                <label className="inline-flex items-center gap-1.5 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={alertEnabled}
                    onChange={(e) => setAlertEnabled(e.target.checked)}
                  />
                  <span className="text-[0.6875rem] font-semibold" style={{ color: colors.inkBold }}>
                    {alertEnabled ? 'On' : 'Off'}
                  </span>
                </label>
              </div>

              {alertEnabled && (
                <div className="space-y-2.5">
                  <div className="grid grid-cols-3 gap-2 items-end">
                    <label className="block space-y-1">
                      <span className="text-[0.6875rem] font-semibold" style={{ color: colors.muted }}>
                        Lead Hours
                      </span>
                      <input
                        type="number"
                        min={0}
                        max={720}
                        value={leadHoursStr}
                        onChange={(e) => setLeadHoursStr(e.target.value.replace(/[^0-9]/g, '').slice(0, 3))}
                        className="w-full px-2.5 py-2 rounded-lg focus:outline-none"
                        style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                      />
                    </label>
                    <label className="block space-y-1">
                      <span className="text-[0.6875rem] font-semibold" style={{ color: colors.muted }}>
                        Lead Mins
                      </span>
                      <input
                        type="number"
                        min={0}
                        max={59}
                        value={leadMinsStr}
                        onChange={(e) => setLeadMinsStr(e.target.value.replace(/[^0-9]/g, '').slice(0, 2))}
                        className="w-full px-2.5 py-2 rounded-lg focus:outline-none"
                        style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                      />
                    </label>
                    <button
                      type="button"
                      disabled={isLeadInvalid || computedLeadMinutes === safeDefaultLead}
                      onClick={() => {
                        if (!isLeadInvalid && onSaveDefaultLeadMinutes) {
                          onSaveDefaultLeadMinutes(computedLeadMinutes);
                        }
                      }}
                      className="px-2.5 py-2 rounded-lg text-[0.6875rem] font-bold border transition disabled:opacity-60"
                      style={{
                        backgroundColor: colors.surface,
                        borderColor: computedLeadMinutes === safeDefaultLead ? colors.teal : colors.border,
                        color: computedLeadMinutes === safeDefaultLead ? colors.teal : colors.inkBold,
                      }}
                    >
                      {computedLeadMinutes === safeDefaultLead ? 'Default ✓' : 'Set Default'}
                    </button>
                  </div>

                  {isLeadInvalid ? (
                    <div className="text-[0.6875rem] font-semibold" style={{ color: colors.red }}>
                      Enter at least 1 minute of advance lead time.
                    </div>
                  ) : (
                    previewEarlyTriggerMs !== null &&
                    previewUtcMs !== null && (
                      <div
                        className="p-2 rounded-lg text-[0.6875rem] flex items-start gap-1.5 border"
                        style={{
                          backgroundColor:
                            previewEarlyTriggerMs <= nowMs ? colors.redLight : colors.surface,
                          borderColor:
                            previewEarlyTriggerMs <= nowMs ? colors.amber : colors.border,
                          color:
                            previewEarlyTriggerMs <= nowMs ? colors.inkBold : colors.tealDark,
                        }}
                      >
                        <Clock className="w-3.5 h-3.5 mt-0.5 flex-shrink-0" />
                        <span>
                          {previewUtcMs <= nowMs
                            ? 'Target date/time is already in the past — future alerts will not fire unless moved forward.'
                            : previewEarlyTriggerMs <= nowMs
                            ? `Early alert time (${StudyDateUtils.formatHumanDateTimeLocal(previewEarlyTriggerMs)}) already passed; ${
                                alertAtZero ? 'only 0m on-time alert will fire.' : 'reduce lead time so it can fire.'
                              }`
                            : `Early alert fires ${StudyDateUtils.formatHumanDateTimeLocal(
                                previewEarlyTriggerMs
                              )} (${formatLeadDuration(computedLeadMinutes)} before)`}
                        </span>
                      </div>
                    )
                  )}

                  <input
                    type="text"
                    value={alertMessage}
                    onChange={(e) => setAlertMessage(e.target.value)}
                    placeholder="What is going to happen (e.g., Hall gate opens — carry admit card)"
                    className="w-full px-3 py-2 rounded-lg focus:outline-none"
                    style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                  />

                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => setVibrateOnAlert((v) => !v)}
                      className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-[0.6875rem] font-bold border transition"
                      style={
                        vibrateOnAlert
                          ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                          : { backgroundColor: colors.surface, color: colors.muted, borderColor: colors.border }
                      }
                    >
                      <Vibrate className="w-3.5 h-3.5" />
                      {vibrateOnAlert ? 'Vibrate: On' : 'Vibrate: Off'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setAlertSoundEnabled((s) => !s)}
                      className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-[0.6875rem] font-bold border transition"
                      style={
                        alertSoundEnabled
                          ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                          : { backgroundColor: colors.surface, color: colors.muted, borderColor: colors.border }
                      }
                    >
                      <Volume2 className="w-3.5 h-3.5" />
                      {alertSoundEnabled ? 'Sound: On' : 'Sound: Off'}
                    </button>
                  </div>

                  <label className="inline-flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={alertAtZero}
                      onChange={(e) => setAlertAtZero(e.target.checked)}
                    />
                    <span className="text-[0.6875rem]" style={{ color: colors.ink }}>
                      Also alert at exact countdown time (0m)
                    </span>
                  </label>

                  {(readiness.blockingIssues.length > 0 || readiness.advisoryWarnings.length > 0) && (
                    <div
                      className="p-2.5 rounded-lg border space-y-1.5 text-[0.6875rem]"
                      style={{
                        backgroundColor: readiness.blockingIssues.length > 0 ? colors.redLight : colors.surface,
                        borderColor: readiness.blockingIssues.length > 0 ? colors.red : colors.amber,
                        color: colors.ink,
                      }}
                    >
                      <div className="flex items-center gap-1.5 font-bold" style={{ color: readiness.blockingIssues.length > 0 ? colors.red : colors.amber }}>
                        <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
                        <span>Web Alert Readiness</span>
                      </div>
                      {readiness.blockingIssues.map((msg, i) => (
                        <div key={`blk-${i}`} className="font-medium" style={{ color: colors.inkBold }}>
                          • {msg}
                        </div>
                      ))}
                      {readiness.advisoryWarnings.map((msg, i) => (
                        <div key={`adv-${i}`} style={{ color: colors.muted }}>
                          • {msg}
                        </div>
                      ))}
                      {readiness.notificationsSupported && readiness.notificationPermission === 'default' && (
                        <button
                          type="button"
                          onClick={async () => {
                            await requestBrowserNotificationPermission();
                            setReadiness(inspectWebAlertReadiness());
                          }}
                          className="mt-1 px-2.5 py-1.5 rounded-lg font-bold border"
                          style={{ backgroundColor: colors.surface, borderColor: colors.teal, color: colors.tealDark }}
                        >
                          Allow Browser Alerts
                        </button>
                      )}
                    </div>
                  )}

                  <button
                    type="button"
                    onClick={async () => {
                      const msg = await testWebCountdownAlert({
                        title,
                        customMessage: alertMessage,
                        leadMinutes: computedLeadMinutes,
                        vibrate: vibrateOnAlert,
                        sound: alertSoundEnabled,
                      });
                      setTestStatus(msg);
                      setReadiness(inspectWebAlertReadiness());
                    }}
                    className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-[0.6875rem] font-bold border transition"
                    style={{ backgroundColor: colors.surface, borderColor: colors.teal, color: colors.tealDark }}
                  >
                    <Vibrate className="w-3.5 h-3.5" />
                    Test Vibration & Alert Now
                  </button>

                  {testStatus && (
                    <div className="text-[0.6875rem] font-medium" style={{ color: colors.tealDark }}>
                      {testStatus}
                    </div>
                  )}
                </div>
              )}
            </div>

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
                disabled={isLeadInvalid}
                className="flex items-center justify-center gap-1 px-4 py-2 rounded-lg font-bold whitespace-nowrap disabled:opacity-50"
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
                      <div className="min-w-0 flex-1 space-y-0.5">
                        <div className="text-[0.6875rem] break-words" style={{ color: colors.ink }}>
                          {StudyDateUtils.formatCountdownSchedule(cd)}
                        </div>
                        {(() => {
                          const earlyMs = computeEarlyAlertTriggerMillis(cd);
                          const leadVal = clampLeadMinutes(cd.alertLeadMinutes);
                          const isPastTarget = remMs !== null && remMs <= 0;
                          const earlyMissed = Boolean(cd.alertEnabled && earlyMs !== null && earlyMs <= nowMs && !isPastTarget);
                          const line = !cd.alertEnabled
                            ? 'Alerts off'
                            : isPastTarget
                            ? 'Target passed — alert inactive'
                            : earlyMissed
                            ? `Early ${formatLeadDuration(leadVal)} window passed${cd.alertAtZero !== false ? ' • 0m alert active' : ''}`
                            : `Alerts ${formatLeadDuration(leadVal)} before${cd.vibrateOnAlert !== false ? ' • Vibrate' : ''}${
                                cd.alertSoundEnabled === false ? ' • Silent' : ''
                              }${cd.alertAtZero !== false ? ' • +0m' : ''}`;
                          return (
                            <div
                              className="text-[0.6875rem] font-medium"
                              style={{
                                color: !cd.alertEnabled || isPastTarget
                                  ? colors.muted
                                  : earlyMissed
                                  ? colors.amber
                                  : colors.tealDark,
                              }}
                            >
                              {line}
                            </div>
                          );
                        })()}
                        {cd.alertEnabled && cd.alertMessage && (
                          <div className="text-[0.6875rem] font-semibold line-clamp-2" style={{ color: colors.inkBold }}>
                            Note: {cd.alertMessage}
                          </div>
                        )}
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
                            const editLead = clampLeadMinutes(cd.alertLeadMinutes);
                            setAlertEnabled(Boolean(cd.alertEnabled));
                            setLeadHoursStr(String(Math.floor(editLead / 60)));
                            setLeadMinsStr(String(editLead % 60));
                            setAlertMessage(cd.alertMessage || '');
                            setVibrateOnAlert(cd.vibrateOnAlert !== false);
                            setAlertSoundEnabled(cd.alertSoundEnabled !== false);
                            setAlertAtZero(cd.alertAtZero !== false);
                            setTestStatus(null);
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
