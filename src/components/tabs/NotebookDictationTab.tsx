import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  FileText,
  Mic,
  Pause,
  Play,
  RotateCcw,
  Sliders,
  TrendingUp,
  Volume2,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  DEFAULT_KNOWLEDGE_RULE,
  DictationConfig,
  DictationElevationInterval,
  DictationPunctuationMode,
  StudyNoteRecord,
} from '../../types/phase4';
import {
  cancelSpeech,
  isSpeechSupported,
  resolveSpeechLocale,
  speakText,
} from '../../utils/browserSpeech';
import { DictationEngine } from '../../utils/dictationEngine';
import { StudyNoteParser } from '../../utils/studyNoteParser';

type PlaybackPhase = 'IDLE' | 'SPEAKING' | 'PAUSING' | 'PAUSED' | 'COMPLETED';

interface NotebookDictationTabProps {
  studyNotes: StudyNoteRecord[];
  initialNoteId?: string | null;
  dictationConfig: DictationConfig;
  onSaveConfig: (cfg: DictationConfig) => void;
}

export const NotebookDictationTab: React.FC<NotebookDictationTabProps> = ({
  studyNotes,
  initialNoteId,
  dictationConfig,
  onSaveConfig,
}) => {
  const { colors } = useTheme();
  const speechAvailable = useMemo(() => isSpeechSupported(), []);

  const [sourceMode, setSourceMode] = useState<'NOTE' | 'CUSTOM'>(
    studyNotes.length > 0 ? 'NOTE' : 'CUSTOM'
  );
  const [selectedNoteId, setSelectedNoteId] = useState<string>(
    initialNoteId || studyNotes[0]?.id || ''
  );
  const [customText, setCustomText] = useState<string>(
    `## Practice Dictation Passage\n1. **Constitutional Morality** -> Adherence to the core norms of constitutional democracy beyond mere literal compliance with statutory procedure.\n  - Key Takeaway: Courts invoke this doctrine to safeguard fundamental rights against majoritarian overreach.`
  );
  const [speechLanguage, setSpeechLanguage] = useState<string>('English');
  const [englishAccent, setEnglishAccent] = useState<string>('UK');
  const [proofreadVisible, setProofreadVisible] = useState<boolean>(false);

  const [passageIdx, setPassageIdx] = useState<number>(0);
  const [phraseIdx, setPhraseIdx] = useState<number>(0);
  const [phase, setPhase] = useState<PlaybackPhase>('IDLE');
  const [liveWpm, setLiveWpm] = useState<number>(
    dictationConfig.elevatingEnabled ? dictationConfig.startWpm : dictationConfig.targetWpm
  );
  const [pauseRemainingMs, setPauseRemainingMs] = useState<number>(0);
  const [pauseTotalMs, setPauseTotalMs] = useState<number>(0);
  const [elapsedActiveSec, setElapsedActiveSec] = useState<number>(0);

  // Sync external note handoff from Knowledge Studio
  useEffect(() => {
    if (initialNoteId && studyNotes.some((n) => n.id === initialNoteId)) {
      setSourceMode('NOTE');
      setSelectedNoteId(initialNoteId);
      setPassageIdx(0);
      setPhraseIdx(0);
      setPhase('IDLE');
      cancelSpeech();
    }
  }, [initialNoteId, studyNotes]);

  const activeNote = useMemo(
    () => studyNotes.find((n) => n.id === selectedNoteId) || studyNotes[0] || null,
    [studyNotes, selectedNoteId]
  );

  const passages = useMemo(() => {
    const raw = sourceMode === 'NOTE' && activeNote ? activeNote.rawContent : customText;
    const rule =
      sourceMode === 'NOTE' && activeNote
        ? activeNote.formatRule
        : { ...DEFAULT_KNOWLEDGE_RULE, collectionType: 'MULTI_LINER' as const };
    const blocks = StudyNoteParser.parseBlocks(raw, rule);
    const extracted = DictationEngine.extractPassages(blocks);
    if (extracted.length > 0) return extracted;
    if (raw.trim()) {
      return [
        {
          id: 'raw-fallback',
          blockIndex: 0,
          title: 'Study Passage',
          badge: 'Passage',
          promptQuestion: '',
          rawDictationText: raw.trim(),
          originalProofreadText: raw.trim(),
        },
      ];
    }
    return [];
  }, [sourceMode, activeNote, customText]);

  const safePassageIdx = Math.min(passageIdx, Math.max(0, passages.length - 1));
  const activePassage = passages[safePassageIdx] || null;

  const phrases = useMemo(() => {
    if (!activePassage) return [];
    return DictationEngine.splitPassageIntoPhrases(
      activePassage.rawDictationText,
      dictationConfig.punctuationMode
    );
  }, [activePassage, dictationConfig.punctuationMode]);

  const safePhraseIdx = Math.min(phraseIdx, Math.max(0, phrases.length - 1));
  const activePhrase = phrases[safePhraseIdx] || null;

  const effectiveConfig = useMemo<DictationConfig>(
    () => ({
      ...dictationConfig,
      targetWpm: dictationConfig.elevatingEnabled ? liveWpm : dictationConfig.targetWpm,
    }),
    [dictationConfig, liveWpm]
  );

  // Refs for clean async playback orchestration
  const phaseRef = useRef<PlaybackPhase>(phase);
  phaseRef.current = phase;
  const fallbackTimerRef = useRef<number | null>(null);
  const pauseIntervalRef = useRef<number | null>(null);
  const safetySpeechTimerRef = useRef<number | null>(null);

  const clearAllTimers = useCallback(() => {
    if (fallbackTimerRef.current !== null) {
      window.clearTimeout(fallbackTimerRef.current);
      fallbackTimerRef.current = null;
    }
    if (pauseIntervalRef.current !== null) {
      window.clearInterval(pauseIntervalRef.current);
      pauseIntervalRef.current = null;
    }
    if (safetySpeechTimerRef.current !== null) {
      window.clearTimeout(safetySpeechTimerRef.current);
      safetySpeechTimerRef.current = null;
    }
  }, []);

  const stopPlayback = useCallback(
    (nextPhase: PlaybackPhase = 'IDLE') => {
      clearAllTimers();
      cancelSpeech();
      setPauseRemainingMs(0);
      setPauseTotalMs(0);
      setPhase(nextPhase);
    },
    [clearAllTimers]
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      clearAllTimers();
      cancelSpeech();
    };
  }, [clearAllTimers]);

  // Progressive time-based WPM elevation counter
  useEffect(() => {
    if (phase !== 'SPEAKING' && phase !== 'PAUSING') return;
    const t = window.setInterval(() => {
      setElapsedActiveSec((s) => s + 1);
    }, 1000);
    return () => window.clearInterval(t);
  }, [phase]);

  useEffect(() => {
    if (!dictationConfig.elevatingEnabled) return;
    if (phase !== 'SPEAKING' && phase !== 'PAUSING') return;
    if (dictationConfig.elevationInterval === 'PER_PASSAGE' || elapsedActiveSec === 0) return;

    const stepSec =
      dictationConfig.elevationInterval === 'EVERY_45S'
        ? 45
        : dictationConfig.elevationInterval === 'EVERY_90S'
        ? 90
        : 60;

    if (elapsedActiveSec % stepSec === 0) {
      setLiveWpm((w) => DictationEngine.computeNextElevatedWpm(w, dictationConfig));
    }
  }, [elapsedActiveSec, dictationConfig, phase]);

  // Trigger phrase speech + pause cycle whenever entering SPEAKING state on (safePassageIdx, safePhraseIdx)
  useEffect(() => {
    if (phase !== 'SPEAKING') return;
    if (!activePhrase) {
      stopPlayback('COMPLETED');
      return;
    }

    clearAllTimers();
    const bcpLang = resolveSpeechLocale(speechLanguage, englishAccent);

    const advanceAfterPause = () => {
      if (phaseRef.current !== 'PAUSING') return;
      if (safePhraseIdx + 1 < phrases.length) {
        setPhraseIdx(safePhraseIdx + 1);
        setPhase('SPEAKING');
      } else if (dictationConfig.autoAdvancePassages && safePassageIdx + 1 < passages.length) {
        if (
          dictationConfig.elevatingEnabled &&
          dictationConfig.elevationInterval === 'PER_PASSAGE'
        ) {
          setLiveWpm((w) => DictationEngine.computeNextElevatedWpm(w, dictationConfig));
        }
        setPassageIdx(safePassageIdx + 1);
        setPhraseIdx(0);
        setPhase('SPEAKING');
      } else {
        stopPlayback('COMPLETED');
      }
    };

    const startPauseCountdown = () => {
      if (phaseRef.current !== 'SPEAKING') return;
      const pauseMs = DictationEngine.computeWritingPauseMs(activePhrase, effectiveConfig);
      setPhase('PAUSING');
      setPauseTotalMs(pauseMs);
      setPauseRemainingMs(pauseMs);
      const startedAt = Date.now();

      pauseIntervalRef.current = window.setInterval(() => {
        if (phaseRef.current !== 'PAUSING') {
          clearAllTimers();
          return;
        }
        const elapsed = Date.now() - startedAt;
        const left = Math.max(0, pauseMs - elapsed);
        setPauseRemainingMs(left);
        if (left <= 0) {
          clearAllTimers();
          advanceAfterPause();
        }
      }, 50);
    };

    const { fallbackTimerId } = speakText(activePhrase.spokenText, {
      lang: bcpLang,
      rate: effectiveConfig.speechBurstRate,
      onEnd: () => {
        if (safetySpeechTimerRef.current !== null) {
          window.clearTimeout(safetySpeechTimerRef.current);
          safetySpeechTimerRef.current = null;
        }
        startPauseCountdown();
      },
      onError: () => {
        if (safetySpeechTimerRef.current !== null) {
          window.clearTimeout(safetySpeechTimerRef.current);
          safetySpeechTimerRef.current = null;
        }
        startPauseCountdown();
      },
    });
    fallbackTimerRef.current = fallbackTimerId;

    // Guard against browsers whose speechSynthesis stalls silently
    const maxExpectedSpeechMs = Math.max(
      2500,
      Math.round((activePhrase.wordCount / 1.4) * 1000) + 1800
    );
    safetySpeechTimerRef.current = window.setTimeout(() => {
      if (phaseRef.current === 'SPEAKING') {
        cancelSpeech();
        startPauseCountdown();
      }
    }, maxExpectedSpeechMs);

    return () => {
      clearAllTimers();
    };
  }, [
    phase,
    activePhrase,
    phrases.length,
    passages.length,
    safePhraseIdx,
    safePassageIdx,
    speechLanguage,
    englishAccent,
    effectiveConfig,
    dictationConfig,
    clearAllTimers,
    stopPlayback,
  ]);

  const handlePlayPause = () => {
    if (phase === 'SPEAKING' || phase === 'PAUSING') {
      stopPlayback('PAUSED');
      return;
    }
    if (phase === 'COMPLETED') {
      setPhraseIdx(0);
    }
    if (phase === 'IDLE') {
      setElapsedActiveSec(0);
      setLiveWpm(
        dictationConfig.elevatingEnabled ? dictationConfig.startWpm : dictationConfig.targetWpm
      );
    }
    setPhase('SPEAKING');
  };

  const handleRestartPassage = () => {
    stopPlayback('IDLE');
    setPhraseIdx(0);
    setElapsedActiveSec(0);
  };

  const handleUpdateConfig = (patch: Partial<DictationConfig>) => {
    const next = DictationEngine.normalizeConfig({ ...dictationConfig, ...patch });
    onSaveConfig(next);
    if (!next.elevatingEnabled && patch.targetWpm !== undefined) {
      setLiveWpm(next.targetWpm);
    }
    if (next.elevatingEnabled && patch.startWpm !== undefined && phase === 'IDLE') {
      setLiveWpm(next.startWpm);
    }
  };

  const pauseProgressRatio =
    pauseTotalMs > 0 ? Math.max(0, Math.min(1, pauseRemainingMs / pauseTotalMs)) : 0;

  return (
    <div className="space-y-5">
      {/* Header Banner */}
      <div
        className="p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
            <Mic className="w-5 h-5" style={{ color: colors.teal }} />
            Notebook Dictation Lab (Cadence &amp; WPM Trainer)
          </h3>
          <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
            Dictates structured study notes phrase-by-phrase with adjustable writing WPM, punctuation cues, progressive acceleration, and Blind vs Proofread mode.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span
            className="px-2.5 py-1 rounded-full font-bold border"
            style={{
              backgroundColor: speechAvailable ? colors.greenLight : colors.amberLight,
              color: speechAvailable ? colors.greenDark : colors.amber,
              borderColor: speechAvailable ? colors.green : colors.amber,
            }}
          >
            {speechAvailable ? 'Web Speech Audio Ready' : 'Visual Cadence Timer Mode'}
          </span>

          <button
            type="button"
            onClick={() => setProofreadVisible((v) => !v)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border font-bold"
            style={
              proofreadVisible
                ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                : { backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }
            }
          >
            {proofreadVisible ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
            {proofreadVisible ? 'Proofread Visible' : 'Blind Writing Mode'}
          </button>
        </div>
      </div>

      {/* Main Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Left: Source & Cadence Settings */}
        <div className="lg:col-span-5 space-y-4 text-xs">
          <div
            className="p-4 rounded-xl border space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-between">
              <span className="font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
                <FileText className="w-3.5 h-3.5" />
                Dictation Material
              </span>
              <div className="flex rounded-lg border p-0.5" style={{ borderColor: colors.border }}>
                <button
                  type="button"
                  onClick={() => {
                    stopPlayback('IDLE');
                    setSourceMode('NOTE');
                    setPassageIdx(0);
                    setPhraseIdx(0);
                  }}
                  className="px-2.5 py-1 rounded-md font-bold"
                  style={
                    sourceMode === 'NOTE'
                      ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                      : { color: colors.ink }
                  }
                >
                  Study Note
                </button>
                <button
                  type="button"
                  onClick={() => {
                    stopPlayback('IDLE');
                    setSourceMode('CUSTOM');
                    setPassageIdx(0);
                    setPhraseIdx(0);
                  }}
                  className="px-2.5 py-1 rounded-md font-bold"
                  style={
                    sourceMode === 'CUSTOM'
                      ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                      : { color: colors.ink }
                  }
                >
                  Paste Text
                </button>
              </div>
            </div>

            {sourceMode === 'NOTE' ? (
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Choose Study Note ({studyNotes.length})
                </label>
                <select
                  value={activeNote?.id || ''}
                  onChange={(e) => {
                    stopPlayback('IDLE');
                    setSelectedNoteId(e.target.value);
                    setPassageIdx(0);
                    setPhraseIdx(0);
                  }}
                  className="w-full px-3 py-2 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  {studyNotes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.title} ({n.subject})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Ad-hoc Dictation Text
                </label>
                <textarea
                  value={customText}
                  onChange={(e) => {
                    stopPlayback('IDLE');
                    setCustomText(e.target.value);
                  }}
                  rows={5}
                  className="w-full p-2.5 rounded-lg border font-mono text-xs"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Speech Language
                </label>
                <select
                  value={speechLanguage}
                  onChange={(e) => setSpeechLanguage(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  {['English', 'Hindi', 'Japanese', 'Korean', 'French', 'German', 'Spanish'].map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  English Voice Accent
                </label>
                <select
                  value={englishAccent}
                  onChange={(e) => setEnglishAccent(e.target.value)}
                  disabled={speechLanguage !== 'English'}
                  className="w-full px-2.5 py-1.5 rounded-lg border disabled:opacity-50"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="UK">British (en-GB)</option>
                  <option value="US">American (en-US)</option>
                </select>
              </div>
            </div>
          </div>

          {/* Cadence & Elevation Controls */}
          <div
            className="p-4 rounded-xl border space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <h4 className="font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
              <Sliders className="w-3.5 h-3.5" />
              Cadence &amp; Punctuation Controls
            </h4>

            <div>
              <div className="flex justify-between font-semibold mb-1" style={{ color: colors.inkBold }}>
                <span>Target Writing Pace</span>
                <span style={{ color: colors.teal }}>
                  {dictationConfig.elevatingEnabled
                    ? `Live: ${liveWpm} WPM (Range ${dictationConfig.startWpm}→${dictationConfig.targetMaxWpm})`
                    : `${dictationConfig.targetWpm} WPM`}
                </span>
              </div>
              <input
                type="range"
                min={25}
                max={120}
                step={5}
                value={dictationConfig.targetWpm}
                onChange={(e) => handleUpdateConfig({ targetWpm: Number(e.target.value) })}
                disabled={dictationConfig.elevatingEnabled}
                className="w-full accent-teal-600"
              />
            </div>

            <div>
              <div className="flex justify-between font-semibold mb-1" style={{ color: colors.inkBold }}>
                <span>Voice Burst Rate</span>
                <span style={{ color: colors.teal }}>{dictationConfig.speechBurstRate.toFixed(2)}x</span>
              </div>
              <input
                type="range"
                min={0.8}
                max={1.35}
                step={0.05}
                value={dictationConfig.speechBurstRate}
                onChange={(e) => handleUpdateConfig({ speechBurstRate: Number(e.target.value) })}
                className="w-full accent-teal-600"
              />
            </div>

            <div>
              <label className="block font-semibold mb-1" style={{ color: colors.inkBold }}>
                Spoken Punctuation Mode
              </label>
              <div className="grid grid-cols-3 gap-1.5">
                {(
                  [
                    { id: 'OFF' as DictationPunctuationMode, label: 'Clean Speech' },
                    { id: 'ESSENTIAL' as DictationPunctuationMode, label: 'Essential (, . ?)' },
                    { id: 'FULL_VERBATIM' as DictationPunctuationMode, label: 'Full Verbatim' },
                  ]
                ).map((pm) => (
                  <button
                    key={pm.id}
                    type="button"
                    onClick={() => handleUpdateConfig({ punctuationMode: pm.id })}
                    className="px-2 py-1.5 rounded-lg border font-bold text-[11px]"
                    style={
                      dictationConfig.punctuationMode === pm.id
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                        : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                    }
                  >
                    {pm.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Progressive WPM Elevation */}
            <div
              className="p-3 rounded-lg border space-y-2"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <label className="flex items-center justify-between cursor-pointer font-bold" style={{ color: colors.inkBold }}>
                <span className="flex items-center gap-1.5">
                  <TrendingUp className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                  Progressive WPM Elevation
                </span>
                <input
                  type="checkbox"
                  checked={dictationConfig.elevatingEnabled}
                  onChange={(e) => {
                    const en = e.target.checked;
                    handleUpdateConfig({ elevatingEnabled: en });
                    if (en) setLiveWpm(dictationConfig.startWpm);
                  }}
                />
              </label>

              {dictationConfig.elevatingEnabled && (
                <div className="grid grid-cols-3 gap-2 pt-1">
                  <div>
                    <label className="block text-[10px]" style={{ color: colors.muted }}>
                      Start WPM
                    </label>
                    <input
                      type="number"
                      min={25}
                      max={100}
                      step={5}
                      value={dictationConfig.startWpm}
                      onChange={(e) => handleUpdateConfig({ startWpm: Number(e.target.value) })}
                      className="w-full px-2 py-1 rounded border"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                  <div>
                    <label className="block text-[10px]" style={{ color: colors.muted }}>
                      Target Max
                    </label>
                    <input
                      type="number"
                      min={30}
                      max={120}
                      step={5}
                      value={dictationConfig.targetMaxWpm}
                      onChange={(e) => handleUpdateConfig({ targetMaxWpm: Number(e.target.value) })}
                      className="w-full px-2 py-1 rounded border"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                  <div>
                    <label className="block text-[10px]" style={{ color: colors.muted }}>
                      Interval
                    </label>
                    <select
                      value={dictationConfig.elevationInterval}
                      onChange={(e) =>
                        handleUpdateConfig({ elevationInterval: e.target.value as DictationElevationInterval })
                      }
                      className="w-full px-1.5 py-1 rounded border"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="EVERY_45S">45s (+{dictationConfig.stepWpm})</option>
                      <option value="EVERY_60S">60s (+{dictationConfig.stepWpm})</option>
                      <option value="EVERY_90S">90s (+{dictationConfig.stepWpm})</option>
                      <option value="PER_PASSAGE">Per Passage</option>
                    </select>
                  </div>
                </div>
              )}

              <label className="flex items-center gap-2 cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={dictationConfig.autoAdvancePassages}
                  onChange={(e) => handleUpdateConfig({ autoAdvancePassages: e.target.checked })}
                />
                <span style={{ color: colors.ink }}>Auto-advance to next passage</span>
              </label>
            </div>
          </div>
        </div>

        {/* Right: Active Dictation Player Stage */}
        <div className="lg:col-span-7 space-y-4">
          {!activePassage ? (
            <div
              className="p-10 rounded-xl border text-center space-y-2"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <Volume2 className="w-8 h-8 mx-auto" style={{ color: colors.muted }} />
              <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
                No passages available to dictate.
              </p>
              <p className="text-xs" style={{ color: colors.muted }}>
                Select a populated Study Note or paste sentences in Custom Text mode.
              </p>
            </div>
          ) : (
            <div
              className="rounded-2xl border p-5 space-y-5 shadow-sm"
              style={{ backgroundColor: colors.surface, borderColor: colors.border }}
            >
              {/* Passage Switcher Header */}
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b" style={{ borderColor: colors.borderLight }}>
                <div>
                  <div className="flex items-center gap-2">
                    <span
                      className="px-2 py-0.5 rounded text-[10px] font-black border"
                      style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                    >
                      Passage {safePassageIdx + 1} / {passages.length}
                    </span>
                    <span className="text-[11px] font-semibold" style={{ color: colors.muted }}>
                      {activePassage.badge}
                    </span>
                  </div>
                  <h4 className="text-sm font-black mt-1" style={{ color: colors.inkBold }}>
                    {activePassage.title}
                  </h4>
                </div>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    disabled={safePassageIdx === 0}
                    onClick={() => {
                      stopPlayback('IDLE');
                      setPassageIdx((i) => Math.max(0, i - 1));
                      setPhraseIdx(0);
                    }}
                    className="p-1.5 rounded-lg border disabled:opacity-40"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
                    title="Previous Passage"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    disabled={safePassageIdx >= passages.length - 1}
                    onClick={() => {
                      stopPlayback('IDLE');
                      setPassageIdx((i) => Math.min(passages.length - 1, i + 1));
                      setPhraseIdx(0);
                    }}
                    className="p-1.5 rounded-lg border disabled:opacity-40"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
                    title="Next Passage"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Status Banner & Countdown */}
              <div
                className="p-4 rounded-xl border space-y-3"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2 font-bold" style={{ color: colors.inkBold }}>
                    <span
                      className="w-2.5 h-2.5 rounded-full"
                      style={{
                        backgroundColor:
                          phase === 'SPEAKING'
                            ? colors.teal
                            : phase === 'PAUSING'
                            ? colors.amber
                            : phase === 'COMPLETED'
                            ? colors.green
                            : colors.muted,
                      }}
                    />
                    <span>
                      {phase === 'IDLE' && 'Ready to Dictate'}
                      {phase === 'SPEAKING' && `Speaking Phrase ${safePhraseIdx + 1} of ${phrases.length}...`}
                      {phase === 'PAUSING' && `Writing Pause (${(pauseRemainingMs / 1000).toFixed(1)}s left)`}
                      {phase === 'PAUSED' && `Paused at Phrase ${safePhraseIdx + 1} of ${phrases.length}`}
                      {phase === 'COMPLETED' && 'Passage Dictation Completed'}
                    </span>
                  </div>
                  <span className="font-mono text-[11px]" style={{ color: colors.teal }}>
                    {effectiveConfig.targetWpm} WPM
                  </span>
                </div>

                {/* Writing Pause Bar */}
                <div className="w-full h-2 rounded-full overflow-hidden" style={{ backgroundColor: colors.surface }}>
                  <div
                    className="h-full transition-all duration-75"
                    style={{
                      width:
                        phase === 'PAUSING'
                          ? `${Math.round(pauseProgressRatio * 100)}%`
                          : phase === 'SPEAKING'
                          ? '100%'
                          : `${Math.round(((safePhraseIdx + (phase === 'COMPLETED' ? 1 : 0)) / Math.max(1, phrases.length)) * 100)}%`,
                      backgroundColor: phase === 'PAUSING' ? colors.amber : colors.teal,
                    }}
                  />
                </div>

                {/* Transport Controls */}
                <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                  <button
                    type="button"
                    disabled={safePhraseIdx === 0}
                    onClick={() => {
                      const prevP = Math.max(0, safePhraseIdx - 1);
                      setPhraseIdx(prevP);
                      if (phase === 'SPEAKING' || phase === 'PAUSING') {
                        setPhase('SPEAKING');
                      }
                    }}
                    className="px-3 py-2 rounded-lg border text-xs font-bold disabled:opacity-40"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                  >
                    Prev Phrase
                  </button>

                  <button
                    type="button"
                    onClick={handlePlayPause}
                    className="flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-black shadow transition"
                    style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                  >
                    {phase === 'SPEAKING' || phase === 'PAUSING' ? (
                      <>
                        <Pause className="w-4 h-4" />
                        Pause
                      </>
                    ) : (
                      <>
                        <Play className="w-4 h-4" />
                        {phase === 'PAUSED' ? 'Resume' : phase === 'COMPLETED' ? 'Replay' : 'Start Dictation'}
                      </>
                    )}
                  </button>

                  <button
                    type="button"
                    disabled={safePhraseIdx >= phrases.length - 1}
                    onClick={() => {
                      const nextP = Math.min(phrases.length - 1, safePhraseIdx + 1);
                      setPhraseIdx(nextP);
                      if (phase === 'SPEAKING' || phase === 'PAUSING') {
                        setPhase('SPEAKING');
                      }
                    }}
                    className="px-3 py-2 rounded-lg border text-xs font-bold disabled:opacity-40"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                  >
                    Next Phrase
                  </button>

                  <button
                    type="button"
                    onClick={handleRestartPassage}
                    className="p-2 rounded-lg border"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                    title="Restart Passage"
                  >
                    <RotateCcw className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Blind Mode Mask OR Proofread Transcript */}
              {!proofreadVisible ? (
                <div
                  className="p-8 rounded-xl border text-center space-y-2"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <EyeOff className="w-7 h-7 mx-auto" style={{ color: colors.teal }} />
                  <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
                    Blind Dictation Active — Text Hidden for Paper Writing
                  </p>
                  <p className="text-xs max-w-md mx-auto" style={{ color: colors.muted }}>
                    Write each dictated phrase in your notebook as you hear it. Switch to{' '}
                    <button
                      type="button"
                      onClick={() => setProofreadVisible(true)}
                      className="underline font-bold"
                      style={{ color: colors.tealDark }}
                    >
                      Proofread Visible
                    </button>{' '}
                    afterward to check spelling and formulas.
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="text-[11px] font-bold uppercase tracking-wider" style={{ color: colors.muted }}>
                    Interactive Phrase Timeline (Click any phrase to jump &amp; dictate)
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {phrases.map((ph, idx) => {
                      const isCur = idx === safePhraseIdx;
                      return (
                        <button
                          key={ph.index}
                          type="button"
                          onClick={() => {
                            setPhraseIdx(idx);
                            setPhase('SPEAKING');
                          }}
                          className="px-2.5 py-1.5 rounded-lg border text-xs text-left transition"
                          style={
                            isCur
                              ? {
                                  backgroundColor: colors.tealLight,
                                  borderColor: colors.teal,
                                  color: colors.inkBold,
                                  fontWeight: 700,
                                }
                              : {
                                  backgroundColor: colors.surfaceVariant,
                                  borderColor: colors.borderLight,
                                  color: colors.ink,
                                }
                          }
                        >
                          {ph.displayText}
                        </button>
                      );
                    })}
                  </div>

                  {activePhrase && dictationConfig.punctuationMode !== 'OFF' && (
                    <div
                      className="p-2.5 rounded-lg border text-[11px] font-mono"
                      style={{ backgroundColor: colors.blueLight, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <strong>Spoken Cue Preview:</strong> {activePhrase.spokenText}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NotebookDictationTab;
