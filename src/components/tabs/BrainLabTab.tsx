import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  Brain,
  Clock,
  Flame,
  HeartPulse,
  Lightbulb,
  Play,
  Volume2,
  VolumeX,
  Zap,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  BRAIN_LAB_ACTIVITIES,
  BRAIN_LAB_PILLARS,
  type BrainLabActivityId,
  type BrainLabPillar,
  type BrainLabSettings,
  type BrainLabStats,
} from '../../types/phase5';
import { playSubtleChime } from '../../utils/brainLabEngines';
import { formatMmSs } from '../../utils/studyPlannerEngine';
import { CorsiSpanDrill, NBackDrill, StroopSwitchDrill } from '../brainlab/MemoryDrills';
import {
  ArgumentCritiqueDrill,
  DataSufficiencyDrill,
  SeatingGridDrill,
  SyllogismDrill,
} from '../brainlab/ReasoningDrills';
import {
  GuidedBreathingDrill,
  MentalQuantDrill,
  SchulteTrailDrill,
  VisualSoftResetDrill,
  ZenLogicGridDrill,
} from '../brainlab/QuantAndCalmDrills';

interface BrainLabTabProps {
  stats: BrainLabStats;
  settings: BrainLabSettings;
  onSaveStats: (next: BrainLabStats) => void;
  onSaveSettings: (next: BrainLabSettings) => void;
  onRecordStudySeconds: (secs: number) => void;
  onNotify: (msg: string) => void;
}

export const BrainLabTab: React.FC<BrainLabTabProps> = ({
  stats,
  settings,
  onSaveStats,
  onSaveSettings,
  onRecordStudySeconds,
  onNotify,
}) => {
  const { colors, scalePx } = useTheme();
  const [selectedPillar, setSelectedPillar] = useState<BrainLabPillar | 'ALL'>('ALL');
  const [activeActivityId, setActiveActivityId] = useState<BrainLabActivityId | null>(null);

  // Break Budget Timer (1..10 min) so cognitive drills don't turn into procrastination
  const [budgetActive, setBudgetActive] = useState<boolean>(false);
  const [budgetSecondsLeft, setBudgetSecondsLeft] = useState<number>(
    settings.preferredBudgetMinutes * 60
  );
  const budgetDeadlineRef = useRef<number>(0);

  useEffect(() => {
    if (!budgetActive) {
      setBudgetSecondsLeft(settings.preferredBudgetMinutes * 60);
      return;
    }
    const t = setInterval(() => {
      const rem = Math.max(0, Math.ceil((budgetDeadlineRef.current - Date.now()) / 1000));
      setBudgetSecondsLeft(rem);
      if (rem <= 0) {
        clearInterval(t);
        setBudgetActive(false);
        if (settings.audioEnabled) playSubtleChime(740, 220);
        onNotify('Break Budget Finished — Ready to return to focused study!');
      }
    }, 500);
    return () => clearInterval(t);
  }, [budgetActive, settings.preferredBudgetMinutes, settings.audioEnabled, onNotify]);

  const activeMeta = useMemo(
    () => BRAIN_LAB_ACTIVITIES.find((a) => a.id === activeActivityId) || null,
    [activeActivityId]
  );

  const visibleActivities = useMemo(() => {
    if (selectedPillar === 'ALL') return BRAIN_LAB_ACTIVITIES;
    return BRAIN_LAB_ACTIVITIES.filter((a) => a.pillar === selectedPillar);
  }, [selectedPillar]);

  const handleUpdateStats = (
    updater: (prev: BrainLabStats) => BrainLabStats,
    studySeconds: number = 0
  ) => {
    const isCalm = activeMeta ? !activeMeta.countsAsCognitiveStudy : false;
    const baseUpdated = updater(stats);
    const next: BrainLabStats = {
      ...baseUpdated,
      totalCognitiveSeconds:
        baseUpdated.totalCognitiveSeconds + (!isCalm && studySeconds > 0 ? studySeconds : 0),
      totalCalmSeconds:
        baseUpdated.totalCalmSeconds + (isCalm && studySeconds > 0 ? studySeconds : 0),
      lastUpdatedAtMillis: Date.now(),
    };
    onSaveStats(next);
    if (studySeconds > 0 && (!isCalm || settings.countCalmInDailyStudyTime)) {
      onRecordStudySeconds(studySeconds);
    }
  };

  const statBadgeForActivity = (id: BrainLabActivityId): string => {
    switch (id) {
      case 'N_BACK':
        return stats.bestNBackAccuracy > 0
          ? `Best: ${stats.bestNBackTier}-Back (${stats.bestNBackAccuracy}%)`
          : 'Unstarted';
      case 'CORSI_SPAN':
        return stats.bestCorsiReverseSpan > 0
          ? `Rev Span: ${stats.bestCorsiReverseSpan} • Fwd: ${stats.bestCorsiForwardSpan}`
          : 'Span 4+';
      case 'STROOP_SWITCH':
        return stats.bestStroopAccuracy > 0
          ? `${stats.bestStroopAccuracy}% (${stats.bestStroopReactionMs}ms)`
          : '15 Trials';
      case 'SYLLOGISM':
        return `${stats.syllogismCorrect}/${stats.syllogismSolved} Solved`;
      case 'CONSTRAINT_SEATING':
        return `${stats.constraintSolved} Solved`;
      case 'ARGUMENT_CRITIQUE':
        return `${stats.argumentSolved} Critiqued`;
      case 'DATA_SUFFICIENCY':
        return `${stats.dataSufficiencySolved} Checked`;
      case 'SCHULTE_TRAIL':
        return stats.bestSchulte5x5Ms > 0
          ? `5×5 Best: ${(stats.bestSchulte5x5Ms / 1000).toFixed(1)}s`
          : '5×5 / 6×6';
      case 'MENTAL_QUANT':
        return stats.bestQuantBlitzScore > 0 ? `Best: ${stats.bestQuantBlitzScore} in 60s` : '60s Sprint';
      case 'GUIDED_BREATHING':
        return `${stats.calmSessionsCompleted} Calm Sessions`;
      case 'VISUAL_SOFT_RESET':
        return '60s Eye Break';
      case 'ZEN_LOGIC_GRID':
        return `${stats.zenGridsCompleted} Grids Done`;
    }
  };

  // Sub-view: Active Drill Screen
  if (activeMeta) {
    return (
      <div className="space-y-5">
        <div
          className="p-4 rounded-2xl border flex flex-wrap items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setActiveActivityId(null)}
              className="p-2 rounded-xl border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              aria-label="Back to Brain Lab Hub"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div className="min-w-0">
              <h2 className="text-base font-black es-clamp-2" style={{ color: colors.inkBold }}>
                {activeMeta.title}
              </h2>
              <p className="text-[0.6875rem] es-scroll-x" style={{ color: colors.muted }}>
                Exam Transfer: {activeMeta.examTransfer}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-shrink-0">
            {budgetActive && (
              <span
                className="px-3 py-1 rounded-full text-xs font-black tabular-nums"
                style={{ backgroundColor: colors.amberLight, color: colors.amber }}
              >
                Budget: {formatMmSs(budgetSecondsLeft)}
              </span>
            )}
            <button
              type="button"
              onClick={() =>
                onSaveSettings({ ...settings, audioEnabled: !settings.audioEnabled })
              }
              className="p-2 rounded-xl border"
              style={{
                backgroundColor: colors.surface,
                borderColor: colors.border,
                color: settings.audioEnabled ? colors.teal : colors.muted,
              }}
              title="Toggle subtle drill chimes"
            >
              {settings.audioEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <div
          className="p-4 sm:p-6 rounded-2xl border"
          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        >
          {activeMeta.id === 'N_BACK' && (
            <NBackDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'CORSI_SPAN' && (
            <CorsiSpanDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'STROOP_SWITCH' && (
            <StroopSwitchDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'SYLLOGISM' && (
            <SyllogismDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'CONSTRAINT_SEATING' && (
            <SeatingGridDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'ARGUMENT_CRITIQUE' && (
            <ArgumentCritiqueDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'DATA_SUFFICIENCY' && (
            <DataSufficiencyDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'SCHULTE_TRAIL' && (
            <SchulteTrailDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'MENTAL_QUANT' && (
            <MentalQuantDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'GUIDED_BREATHING' && (
            <GuidedBreathingDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'VISUAL_SOFT_RESET' && (
            <VisualSoftResetDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
          {activeMeta.id === 'ZEN_LOGIC_GRID' && (
            <ZenLogicGridDrill stats={stats} settings={settings} onUpdateStats={handleUpdateStats} />
          )}
        </div>
      </div>
    );
  }

  // Hub View
  return (
    <div className="space-y-6">
      {/* Hub Banner */}
      <div
        className="rounded-2xl border p-4 sm:p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-4"
        style={{ background: colors.heroGradient, borderColor: colors.border, boxShadow: colors.cardShadow }}
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Brain className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            <h2 className="font-black es-clamp-2" style={{ fontSize: scalePx(20), color: colors.inkBold }}>
              Brain &amp; Calm Lab
            </h2>
          </div>
          <p className="text-xs mt-1 es-scroll-x" style={{ color: colors.muted }}>
            12 offline exam-transfer cognitive drills &amp; calm reset exercises with deterministic verification
          </p>
        </div>

        {/* Metrics + Sound + Break Budget Control */}
        <div className="flex flex-wrap items-center gap-2">
          <div
            className="px-3 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.amber }}
          >
            <Flame className="w-3.5 h-3.5" />
            Reasoning Streak: {stats.bestReasoningStreak}
          </div>

          <button
            type="button"
            onClick={() => {
              if (budgetActive) {
                setBudgetActive(false);
              } else {
                budgetDeadlineRef.current = Date.now() + settings.preferredBudgetMinutes * 60 * 1000;
                setBudgetActive(true);
                onNotify(`Started ${settings.preferredBudgetMinutes}-minute Break Budget`);
              }
            }}
            className="px-3 py-1.5 rounded-xl border text-xs font-black flex items-center gap-1.5"
            style={
              budgetActive
                ? { backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.amber }
                : { backgroundColor: colors.teal, borderColor: colors.teal, color: colors.onAccentText }
            }
          >
            <Clock className="w-3.5 h-3.5" />
            {budgetActive
              ? `Budget: ${formatMmSs(budgetSecondsLeft)} (Stop)`
              : `Start ${settings.preferredBudgetMinutes}m Break Budget`}
          </button>

          <select
            value={settings.preferredBudgetMinutes}
            onChange={(e) =>
              onSaveSettings({ ...settings, preferredBudgetMinutes: Number(e.target.value) })
            }
            className="px-2 py-1.5 rounded-xl border text-xs font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            aria-label="Break Budget Minutes"
          >
            {[2, 3, 5, 7, 10].map((m) => (
              <option key={m} value={m}>
                {m} min
              </option>
            ))}
          </select>

          <button
            type="button"
            onClick={() => onSaveSettings({ ...settings, audioEnabled: !settings.audioEnabled })}
            className="p-2 rounded-xl border"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: settings.audioEnabled ? colors.teal : colors.muted,
            }}
            title="Toggle Drill Sound Chimes"
          >
            {settings.audioEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* Settings & Totals Strip */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-1 text-xs">
        <div className="flex flex-wrap gap-1.5">
          <button
            type="button"
            onClick={() => setSelectedPillar('ALL')}
            className="px-3 py-1.5 rounded-xl font-bold border"
            style={
              selectedPillar === 'ALL'
                ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
            }
          >
            All (12)
          </button>
          {BRAIN_LAB_PILLARS.map((p) => {
            const sel = selectedPillar === p.id;
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => setSelectedPillar(p.id)}
                className="px-3 py-1.5 rounded-xl font-bold border"
                style={
                  sel
                    ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                }
              >
                {p.title}
              </button>
            );
          })}
        </div>

        <label className="flex items-center gap-2 font-semibold cursor-pointer" style={{ color: colors.ink }}>
          <input
            type="checkbox"
            checked={settings.countCalmInDailyStudyTime}
            onChange={(e) =>
              onSaveSettings({ ...settings, countCalmInDailyStudyTime: e.target.checked })
            }
          />
          Count Calm resets in Study Time ({Math.round(stats.totalCognitiveSeconds / 60)}m Cog /{' '}
          {Math.round(stats.totalCalmSeconds / 60)}m Calm logged)
        </label>
      </div>

      {/* Activity Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {visibleActivities.map((act) => {
          const pillarMeta = BRAIN_LAB_PILLARS.find((p) => p.id === act.pillar)!;
          const isCalm = !act.countsAsCognitiveStudy;
          return (
            <div
              key={act.id}
              className="p-4 rounded-2xl border flex flex-col justify-between gap-3"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span
                    className="text-[0.625rem] font-black px-2 py-0.5 rounded-full uppercase"
                    style={{
                      backgroundColor: isCalm ? colors.tealLight : colors.surface,
                      color: isCalm ? colors.tealDark : colors.teal,
                    }}
                  >
                    {pillarMeta.badge}
                  </span>
                  <span className="text-[0.6875rem] font-bold" style={{ color: colors.muted }}>
                    ~{act.defaultBudgetMinutes}m
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {act.pillar === 'WORKING_MEMORY' && <Zap className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />}
                  {act.pillar === 'CRITICAL_REASONING' && <Lightbulb className="w-4 h-4 flex-shrink-0" style={{ color: colors.amber }} />}
                  {act.pillar === 'VISUAL_QUANT' && <Flame className="w-4 h-4 flex-shrink-0" style={{ color: colors.red }} />}
                  {act.pillar === 'CALM_RESET' && <HeartPulse className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />}
                  <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                    {act.title}
                  </h3>
                </div>

                <p className="text-xs leading-relaxed" style={{ color: colors.ink }}>
                  {act.shortSummary}
                </p>

                <div
                  className="p-2 rounded-lg border text-[0.6875rem]"
                  style={{ backgroundColor: colors.surface, borderColor: colors.borderLight, color: colors.muted }}
                >
                  <strong>Exam Transfer:</strong> {act.examTransfer}
                </div>
              </div>

              <div className="flex items-center justify-between pt-2 border-t" style={{ borderColor: colors.borderLight }}>
                <span className="text-[0.6875rem] font-bold" style={{ color: colors.teal }}>
                  {statBadgeForActivity(act.id)}
                </span>
                <button
                  type="button"
                  onClick={() => setActiveActivityId(act.id)}
                  className="px-3.5 py-1.5 rounded-xl text-xs font-black flex items-center gap-1.5"
                  style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                >
                  <Play className="w-3.5 h-3.5" />
                  Launch
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default BrainLabTab;
