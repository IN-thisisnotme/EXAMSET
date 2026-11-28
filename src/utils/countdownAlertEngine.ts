import { ExamCountdownTarget } from '../types/exam';
import { StudyDateUtils } from './studyPlannerEngine';

export const DEFAULT_ALERT_LEAD_MINUTES = 120;
export const MIN_ALERT_LEAD_MINUTES = 1;
export const MAX_ALERT_LEAD_MINUTES = 43200; // 30 days
export const FOREGROUND_CATCHUP_WINDOW_MS = 15 * 60_000;

export type CountdownAlertStage = 'EARLY' | 'ZERO' | 'SNOOZE';

export interface FiredCountdownAlert {
  alertKey: string;
  countdownId: string;
  title: string;
  customMessage: string;
  stage: CountdownAlertStage;
  leadMinutes: number;
  targetUtcMillis: number;
  scheduleText: string;
  vibrate: boolean;
  sound: boolean;
}

export interface WebAlertReadiness {
  notificationsSupported: boolean;
  notificationPermission: NotificationPermission | 'unsupported';
  vibrationSupported: boolean;
  audioSupported: boolean;
  blockingIssues: string[];
  advisoryWarnings: string[];
}

export function clampLeadMinutes(minutes: number | undefined): number {
  const num = Number(minutes);
  if (!Number.isFinite(num) || num <= 0) return DEFAULT_ALERT_LEAD_MINUTES;
  return Math.max(MIN_ALERT_LEAD_MINUTES, Math.min(MAX_ALERT_LEAD_MINUTES, Math.round(num)));
}

export function formatLeadDuration(leadMinutes: number): string {
  const mins = clampLeadMinutes(leadMinutes);
  const days = Math.floor(mins / (24 * 60));
  const remHours = Math.floor((mins % (24 * 60)) / 60);
  const remMins = mins % 60;
  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (remHours > 0) parts.push(`${remHours}h`);
  if (remMins > 0 || parts.length === 0) parts.push(`${remMins}m`);
  return parts.join(' ');
}

export function buildCountdownAlertKey(
  countdownId: string,
  stage: CountdownAlertStage,
  triggerMillis: number
): string {
  return `${countdownId}|${stage}|${triggerMillis}`;
}

export function computeEarlyAlertTriggerMillis(cd: ExamCountdownTarget): number | null {
  if (!cd.alertEnabled) return null;
  const targetMs = StudyDateUtils.resolveCountdownTargetMillis(cd);
  if (targetMs === null) return null;
  const lead = clampLeadMinutes(cd.alertLeadMinutes);
  return targetMs - lead * 60_000;
}

export function inspectWebAlertReadiness(): WebAlertReadiness {
  const hasWindow = typeof window !== 'undefined';
  const notificationsSupported = hasWindow && 'Notification' in window;
  const notificationPermission: NotificationPermission | 'unsupported' = notificationsSupported
    ? window.Notification.permission
    : 'unsupported';

  const hasNav = typeof navigator !== 'undefined';
  const vibrationSupported = hasNav && typeof navigator.vibrate === 'function';
  const audioSupported =
    hasWindow &&
    (typeof window.AudioContext === 'function' ||
      typeof (window as unknown as { webkitAudioContext?: unknown }).webkitAudioContext === 'function');

  const blockingIssues: string[] = [];
  if (!notificationsSupported) {
    blockingIssues.push(
      'This browser does not support Web Notifications — countdowns will only alert via in-app popup when this tab is open.'
    );
  } else if (notificationPermission === 'denied') {
    blockingIssues.push(
      'Browser notification permission is blocked — unblock notifications for this site in your browser settings.'
    );
  } else if (notificationPermission === 'default') {
    blockingIssues.push(
      'Browser notification permission is not yet granted — tap "Allow Browser Alerts" below.'
    );
  }

  const advisoryWarnings: string[] = [
    'Web browsers cannot wake a closed tab for offline alarms; keep this ExamSet tab open (or use the Android app for background & Doze alarms).',
  ];

  if (!vibrationSupported) {
    advisoryWarnings.push(
      'Vibration API (navigator.vibrate) is unavailable on this browser/device (e.g. desktop or iOS Safari); alerts will show visually/with chime.'
    );
  }

  return {
    notificationsSupported,
    notificationPermission,
    vibrationSupported,
    audioSupported,
    blockingIssues,
    advisoryWarnings,
  };
}

export async function requestBrowserNotificationPermission(): Promise<NotificationPermission | 'unsupported'> {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  try {
    return await window.Notification.requestPermission();
  } catch {
    return window.Notification.permission;
  }
}

export function vibrateCountdownPattern(): boolean {
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      return navigator.vibrate([380, 150, 380, 150, 520]);
    }
  } catch {
    // ignore hardware/browser restriction
  }
  return false;
}

export async function playWebCountdownChime(): Promise<boolean> {
  if (typeof window === 'undefined') return false;
  const AudioCtx =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioCtx) return false;
  try {
    const ctx = new AudioCtx();
    if (ctx.state === 'suspended') {
      await ctx.resume();
    }
    const now = ctx.currentTime;
    const notes = [587.33, 880, 1174.66]; // D5 -> A5 -> D6 clear bell chime
    notes.forEach((freq, idx) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now + idx * 0.14);
      gain.gain.setValueAtTime(0.001, now + idx * 0.14);
      gain.gain.exponentialRampToValueAtTime(0.22, now + idx * 0.14 + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.14 + 0.45);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now + idx * 0.14);
      osc.stop(now + idx * 0.14 + 0.48);
    });
    window.setTimeout(() => {
      ctx.close().catch(() => {});
    }, 1500);
    return true;
  } catch {
    return false;
  }
}

export function dispatchBrowserCountdownNotification(alert: FiredCountdownAlert): boolean {
  if (typeof window === 'undefined' || !('Notification' in window)) return false;
  if (window.Notification.permission !== 'granted') return false;
  try {
    const leadLabel = formatLeadDuration(alert.leadMinutes);
    const title =
      alert.stage === 'EARLY'
        ? `${alert.title} — in ${leadLabel}`
        : alert.stage === 'ZERO'
        ? `${alert.title} — Starting Now!`
        : `Reminder: ${alert.title}`;
    const body = alert.customMessage
      ? `${alert.customMessage}\nScheduled: ${alert.scheduleText}`
      : `Scheduled: ${alert.scheduleText}`;
    new window.Notification(title, {
      body,
      tag: `examset-countdown-${alert.countdownId}-${alert.stage}`,
      requireInteraction: true,
    });
    return true;
  } catch {
    return false;
  }
}

/**
 * Evaluates countdowns against current clock and returns newly triggered alerts
 * within the 15-minute catch-up window that are not present in `firedKeys`.
 */
export function evaluateDueCountdownAlerts(
  countdowns: ExamCountdownTarget[],
  firedKeys: Set<string>,
  nowMs: number = Date.now()
): FiredCountdownAlert[] {
  const due: FiredCountdownAlert[] = [];
  for (const cd of countdowns) {
    if (!cd.alertEnabled) continue;
    const targetMs = StudyDateUtils.resolveCountdownTargetMillis(cd);
    if (targetMs === null) continue;
    const lead = clampLeadMinutes(cd.alertLeadMinutes);
    const earlyTrigger = targetMs - lead * 60_000;
    const customMessage = (cd.alertMessage || '').trim();
    const vibrate = cd.vibrateOnAlert !== false;
    const sound = cd.alertSoundEnabled !== false;
    const scheduleText = StudyDateUtils.formatCountdownSchedule(cd);

    if (nowMs >= earlyTrigger && nowMs <= earlyTrigger + FOREGROUND_CATCHUP_WINDOW_MS && nowMs < targetMs) {
      const earlyKey = buildCountdownAlertKey(cd.id, 'EARLY', earlyTrigger);
      if (!firedKeys.has(earlyKey)) {
        due.push({
          alertKey: earlyKey,
          countdownId: cd.id,
          title: cd.title,
          customMessage,
          stage: 'EARLY',
          leadMinutes: lead,
          targetUtcMillis: targetMs,
          scheduleText,
          vibrate,
          sound,
        });
      }
    }

    const alertAtZero = cd.alertAtZero !== false;
    if (alertAtZero && nowMs >= targetMs && nowMs <= targetMs + FOREGROUND_CATCHUP_WINDOW_MS) {
      const zeroKey = buildCountdownAlertKey(cd.id, 'ZERO', targetMs);
      if (!firedKeys.has(zeroKey)) {
        due.push({
          alertKey: zeroKey,
          countdownId: cd.id,
          title: cd.title,
          customMessage,
          stage: 'ZERO',
          leadMinutes: lead,
          targetUtcMillis: targetMs,
          scheduleText,
          vibrate,
          sound,
        });
      }
    }
  }
  return due;
}

export async function testWebCountdownAlert(params: {
  title: string;
  customMessage: string;
  leadMinutes: number;
  vibrate: boolean;
  sound: boolean;
}): Promise<string> {
  const vibOk = params.vibrate ? vibrateCountdownPattern() : false;
  const soundOk = params.sound ? await playWebCountdownChime() : false;
  const leadLabel = formatLeadDuration(params.leadMinutes);
  const previewAlert: FiredCountdownAlert = {
    alertKey: `test-${Date.now()}`,
    countdownId: 'test-preview',
    title: params.title.trim() || 'Sample Exam Countdown',
    customMessage:
      params.customMessage.trim() || `Early alert test (${leadLabel} ahead)`,
    stage: 'EARLY',
    leadMinutes: clampLeadMinutes(params.leadMinutes),
    targetUtcMillis: Date.now() + clampLeadMinutes(params.leadMinutes) * 60_000,
    scheduleText: StudyDateUtils.formatHumanDateTimeLocal(
      Date.now() + clampLeadMinutes(params.leadMinutes) * 60_000
    ),
    vibrate: params.vibrate,
    sound: params.sound,
  };
  const notifOk = dispatchBrowserCountdownNotification(previewAlert);
  const readiness = inspectWebAlertReadiness();

  const parts: string[] = [];
  if (params.vibrate) {
    parts.push(vibOk ? 'Vibration sent' : 'Vibration unsupported by this browser/device');
  }
  if (params.sound) {
    parts.push(soundOk ? 'Chime played' : 'Audio blocked by browser');
  }
  if (notifOk) {
    parts.push('Browser notification shown');
  } else if (readiness.notificationPermission !== 'granted') {
    parts.push('Grant browser notifications for system popup');
  }
  return parts.join(' • ') || 'Alert test executed.';
}
