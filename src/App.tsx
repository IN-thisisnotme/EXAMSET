import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Archive,
  ArrowLeft,
  Bell,
  BookMarked,
  BookOpen,
  Brain,
  CalendarCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Eye,
  FileText,
  Folder,
  Globe,
  HelpCircle,
  Menu,
  Mic,
  MoreHorizontal,
  Palette,
  Printer,
  Share2,
  Sparkles,
  Type,
  X,
} from 'lucide-react';
import {
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  QuestionPaper,
} from './types/exam';
import {
  DictationConfig,
  FlashcardSrsState,
  HeadwordQueueItem,
  KnowledgeCollectionType,
  KnowledgeCoursesMap,
  LanguageWordOfTheDayState,
  PromptPreset,
  StudyNoteRecord,
  VocabularyDeck,
} from './types/phase4';
import {
  type AutoBackupSchedule,
  type BackupManifest,
  type BrainLabSettings,
  type BrainLabStats,
  type DailyStudyAnalytics,
  type GuideDestination,
  type PomodoroConfig,
  type RestoreMode,
  type RestorePreview,
  type StudyDueSuggestion,
  type StudyTodoItem,
  type VaultSnapshotRecord,
} from './types/phase5';
import { ThemeProvider, useTheme } from './theme/ThemeProvider';
import { DEFAULT_ROOT_FOLDER, ExamStore, normalizePaperMetadata } from './data/examStore';
// [DEPRECATED 2026-11-28] Reason: Web starts with a clean empty workspace matching Android without auto-seeding starter notes or vocab decks. Restore if: Starter seed packs are ever explicitly re-enabled.
// import { STARTER_STUDY_NOTES, STARTER_VOCAB_DECKS } from './data/phase4Seeds';
import { applyVaultRestoreToState, buildVaultArchive, type WorkspaceBackupState } from './utils/vaultArchiveEngine';
import { CloudSyncEngine } from './utils/cloudSyncEngine';
import {
  dispatchBrowserCountdownNotification,
  evaluateDueCountdownAlerts,
  formatLeadDuration,
  playWebCountdownChime,
  vibrateCountdownPattern,
  type FiredCountdownAlert,
} from './utils/countdownAlertEngine';
import { ThemePaletteModal } from './components/ThemePaletteModal';
import { FontScaleModal } from './components/FontScaleModal';
import { InputModal } from './components/InputModal';
import { BatchImportModal } from './components/BatchImportModal';
import { EditMetadataModal } from './components/EditMetadataModal';
import { AuditResolutionModal } from './components/AuditResolutionModal';
import { ExamCountdownsModal } from './components/ExamCountdownsModal';
import { StudyNoteEditorModal } from './components/StudyNoteEditorModal';
import { VocabularyImportModal } from './components/VocabularyImportModal';
import { VocabularyAuditModal } from './components/VocabularyAuditModal';
import { HeadwordInboxModal } from './components/HeadwordInboxModal';
import { AppGuideModal } from './components/AppGuideModal';
import { CreatorTab } from './components/tabs/CreatorTab';
import { MasterViewTab } from './components/tabs/MasterViewTab';
import { PrintGeneratorTab } from './components/tabs/PrintGeneratorTab';
import { StudentPortalTab } from './components/tabs/StudentPortalTab';
import { FolderManagerTab } from './components/tabs/FolderManagerTab';
import { KnowledgeStudioTab } from './components/tabs/KnowledgeStudioTab';
import { NotebookDictationTab } from './components/tabs/NotebookDictationTab';
import { VocabularyHubTab } from './components/tabs/VocabularyHubTab';
import { PromptGeneratorTab } from './components/tabs/PromptGeneratorTab';
import { ShareSystemTab } from './components/tabs/ShareSystemTab';
import { StudyPlannerTab } from './components/tabs/StudyPlannerTab';
import { BrainLabTab } from './components/tabs/BrainLabTab';
import { BackupVaultTab } from './components/tabs/BackupVaultTab';

export type ActiveTab =
  | 'creator'
  | 'master'
  | 'print'
  | 'student_portal'
  | 'folders'
  | 'knowledge_studio'
  | 'dictation'
  | 'vocabulary_hub'
  | 'prompt_generator'
  | 'study_planner'
  | 'brain_lab'
  | 'backup_vault'
  | 'share';

export const DEFAULT_PAPER: QuestionPaper = {
  metadata: {
    id: 'paper-default-01',
    title: 'National Science & Technology Olympiad',
    subject: 'General Science',
    topic: 'Physics, Chemistry & Ecology Core',
    setNumber: '01',
    targetExam: 'Standard Assessment',
    rollNumber: '0000000001',
    bookletNumber: '0000001',
    durationMinutes: 30,
    positiveMarks: 2.0,
    negativeMarks: 0.66,
    unattendedMarks: 0.0,
    msqScoringPolicy: 'ALL_OR_NOTHING',
    createdAt: 1700000000000,
    folderId: 'default',
  },
  questions: [
    {
      id: 'q1',
      num: 1,
      question:
        'Which chemical compound is commonly known as dry ice and sublimes directly into gas at -78.5°C?',
      questionType: 'factual',
      options: {
        a: 'Solid Carbon Dioxide (CO₂)',
        b: 'Solid Nitrogen (N₂)',
        c: 'Solid Carbon Monoxide (CO)',
        d: 'Solid Methane (CH₄)',
      },
      correctAnswer: 'A',
      explanation:
        'Dry ice is the solid form of carbon dioxide (CO₂). Under atmospheric pressure, it sublimes directly into vapor without entering a liquid phase.',
    },
    {
      id: 'q2',
      num: 2,
      question: 'What is the approximate velocity of light in a complete vacuum?',
      questionType: 'factual',
      options: {
        a: '3 × 10⁶ m/s',
        b: '3 × 10⁸ m/s',
        c: '3 × 10¹⁰ m/s',
        d: '1.5 × 10⁸ m/s',
      },
      correctAnswer: 'B',
      explanation:
        'The speed of light in vacuum is defined as exactly 299,792,458 m/s, approximately 3 × 10⁸ m/s.',
    },
    {
      id: 'q3',
      num: 3,
      question:
        'Assertion (A): Graphene exhibits exceptionally high electrical conductivity at room temperature.\nReason (R): Each carbon atom in graphene is sp² hybridized, leaving one delocalized pi-electron free to move across the planar honeycomb lattice.',
      questionType: 'assertion-reason',
      options: {
        a: 'Both (A) and (R) are true, and (R) is the correct explanation of (A).',
        b: 'Both (A) and (R) are true, but (R) is not the correct explanation of (A).',
        c: '(A) is true, but (R) is false.',
        d: '(A) is false, but (R) is true.',
      },
      correctAnswer: 'A',
      explanation:
        'The sp² hybridization in a 2D honeycomb lattice creates conjugated delocalized pi bands with very high electron mobility.',
    },
    {
      id: 'q4',
      num: 4,
      question: 'What is the pH value of pure neutral water at standard room temperature (25°C)?',
      questionType: 'factual',
      options: {
        a: '5.5',
        b: '7.0',
        c: '8.5',
        d: '0.0',
      },
      correctAnswer: 'B',
      explanation:
        'Pure water at 25°C has equal concentrations of H⁺ and OH⁻ ions ($10^{-7}$ mol/L), yielding pH = 7.0.',
    },
    {
      id: 'q5',
      num: 5,
      question:
        'Which of the following organelles in eukaryotic cells generate adenosine triphosphate (ATP) via oxidative phosphorylation?',
      questionType: 'conceptual',
      options: {
        a: 'Endoplasmic Reticulum',
        b: 'Golgi Apparatus',
        c: 'Mitochondria',
        d: 'Lysosome',
      },
      correctAnswer: 'C',
      explanation:
        'Mitochondria house the electron transport chain and ATP synthase along their inner cristae.',
    },
  ],
};

const ExamSetWorkspace: React.FC = () => {
  const {
    themeMode,
    setThemeMode,
    preset,
    setPreset,
    fontScaleMode,
    setFontScaleMode,
    colors,
    isDark,
  } = useTheme();
  const [activeTab, setActiveTab] = useState<ActiveTab>('creator');
  const [tabHistory, setTabHistory] = useState<ActiveTab[]>([]);
  const [tabHasSubBack, setTabHasSubBack] = useState<boolean>(false);
  const tabBackHandlerRef = useRef<(() => boolean) | null>(null);
  const studyNoteRequestCloseRef = useRef<(() => void) | null>(null);
  const hasPushedTrapRef = useRef<boolean>(false);

  const registerTabBackHandler = useCallback((handler: (() => boolean) | null) => {
    tabBackHandlerRef.current = handler;
    setTabHasSubBack(Boolean(handler));
  }, []);

  const registerStudyNoteCloseRequest = useCallback((fn: (() => void) | null) => {
    studyNoteRequestCloseRef.current = fn;
  }, []);

  const [papers, setPapers] = useState<QuestionPaper[]>(() => {
    ExamStore.cleanLegacySamplesOnce();
    return ExamStore.loadPapers();
  });
  const [currentPaper, setCurrentPaper] = useState<QuestionPaper | null>(() =>
    ExamStore.getActivePaper()
  );
  const [folders, setFolders] = useState<FolderItem[]>(() =>
    ExamStore.loadFolders(ExamStore.loadPapers())
  );
  const [countdowns, setCountdowns] = useState<ExamCountdownTarget[]>(() =>
    ExamStore.loadCountdowns()
  );
  const [defaultCountdownLeadMinutes, setDefaultCountdownLeadMinutes] = useState<number>(() =>
    ExamStore.loadDefaultCountdownLeadMinutes()
  );
  const [activeCountdownAlert, setActiveCountdownAlert] = useState<FiredCountdownAlert | null>(null);
  const snoozedTimersRef = useRef<Record<string, number>>({});
  const [attempts, setAttempts] = useState<AttemptRecord[]>(() => ExamStore.loadAttempts());

  // Phase 4 Persistent State (Clean start matching Android)
  const [studyNotes, setStudyNotes] = useState<StudyNoteRecord[]>(() =>
    ExamStore.loadStudyNotes()
  );
  const [knowledgeCourses, setKnowledgeCourses] = useState<KnowledgeCoursesMap>(() =>
    ExamStore.loadKnowledgeCoursesMap()
  );
  const [requestedStudyNoteId, setRequestedStudyNoteId] = useState<string | null>(null);
  const [vocabDecks, setVocabDecks] = useState<VocabularyDeck[]>(() =>
    ExamStore.loadVocabDecks()
  );
  const [vocabSrs, setVocabSrs] = useState<Record<string, Record<string, FlashcardSrsState>>>(() =>
    ExamStore.loadVocabSrs()
  );
  const [englishVariant, setEnglishVariant] = useState<string>(() =>
    ExamStore.loadEnglishVariant()
  );
  const [headwordInbox, setHeadwordInbox] = useState<HeadwordQueueItem[]>(() =>
    ExamStore.loadHeadwordInbox()
  );
  const [dismissedHeadwords, setDismissedHeadwords] = useState<string[]>(() =>
    ExamStore.loadDismissedHeadwords()
  );
  const [wotdStates, setWotdStates] = useState<Record<string, LanguageWordOfTheDayState>>(() =>
    ExamStore.loadWotdStates()
  );
  const [promptPresets, setPromptPresets] = useState<PromptPreset[]>(() =>
    ExamStore.loadPromptPresets()
  );
  const [dictationConfig, setDictationConfig] = useState<DictationConfig>(() =>
    ExamStore.loadDictationConfig()
  );
  const [dictationTargetNoteId, setDictationTargetNoteId] = useState<string | null>(null);

  const [notification, setNotification] = useState<string | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);

  // Modal States
  const [showThemeModal, setShowThemeModal] = useState<boolean>(false);
  const [showFontModal, setShowFontModal] = useState<boolean>(false);
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [createDefaultFolderId, setCreateDefaultFolderId] = useState<string>('default');
  const [createInitialText, setCreateInitialText] = useState<string>('');
  const [showBatchImportModal, setShowBatchImportModal] = useState<boolean>(false);
  const [editingMetadataPaper, setEditingMetadataPaper] = useState<QuestionPaper | null>(null);
  const [showAuditModal, setShowAuditModal] = useState<boolean>(false);
  const [showCountdownsModal, setShowCountdownsModal] = useState<boolean>(false);

  // Phase 4 Modal States
  const [showStudyNoteModal, setShowStudyNoteModal] = useState<boolean>(false);
  const [editingStudyNote, setEditingStudyNote] = useState<StudyNoteRecord | null>(null);
  const [studyNotePrefill, setStudyNotePrefill] = useState<{
    collectionType?: KnowledgeCollectionType;
    course?: string;
    subject?: string;
    topic?: string;
  } | null>(null);
  // Phase 5 Persistent State
  const [studyTodos, setStudyTodos] = useState<StudyTodoItem[]>(() => ExamStore.loadStudyTodos());
  const [pomodoroConfig, setPomodoroConfig] = useState<PomodoroConfig>(() =>
    ExamStore.loadPomodoroConfig()
  );
  const [dailyAnalytics, setDailyAnalytics] = useState<Record<string, DailyStudyAnalytics>>(() =>
    ExamStore.loadDailyAnalytics()
  );
  const [brainLabSettings, setBrainLabSettings] = useState<BrainLabSettings>(() =>
    ExamStore.loadBrainLabSettings()
  );
  const [brainLabStats, setBrainLabStats] = useState<BrainLabStats>(() =>
    ExamStore.loadBrainLabStats()
  );
  const [vaultSnapshots, setVaultSnapshots] = useState<VaultSnapshotRecord[]>(() =>
    ExamStore.loadVaultSnapshots()
  );
  const [autoBackupSchedule, setAutoBackupSchedule] = useState<AutoBackupSchedule>(() =>
    ExamStore.loadAutoBackupSchedule()
  );
  const [lastAutoBackupAt, setLastAutoBackupAt] = useState<number>(() =>
    ExamStore.loadLastAutoBackupAt()
  );
  const [railExpanded, setRailExpanded] = useState<boolean>(true);
  const [dismissedTips, setDismissedTips] = useState<string[]>(() => ExamStore.loadDismissedTips());
  const [showAppGuideModal, setShowAppGuideModal] = useState<boolean>(false);

  const [showVocabImportModal, setShowVocabImportModal] = useState<boolean>(false);
  const [vocabImportDefaultLang, setVocabImportDefaultLang] = useState<string>('English');
  const [auditVocabDeck, setAuditVocabDeck] = useState<VocabularyDeck | null>(null);
  const [showHeadwordInboxModal, setShowHeadwordInboxModal] = useState<boolean>(false);

  const showToast = (message: string) => {
    setNotification(message);
    setTimeout(() => {
      setNotification((cur) => (cur === message ? null : cur));
    }, 3200);
  };

  const navigateTab = useCallback(
    (nextTab: ActiveTab, pushHistory: boolean = true) => {
      setMobileMenuOpen(false);
      setActiveTab((prev) => {
        if (prev === nextTab) return prev;
        if (pushHistory) {
          setTabHistory((hist) => [...hist.slice(-19), prev]);
        }
        return nextTab;
      });
    },
    []
  );

  useEffect(() => {
    ExamStore.setActivePaper(currentPaper);
  }, [currentPaper]);

  useEffect(() => {
    void CloudSyncEngine.handleOAuthCallbackIfPresent().then((res) => {
      if (res) {
        showToast(res.message);
        navigateTab('backup_vault', false);
      }
    });
  }, [navigateTab]);

  const fireCountdownAlertFeedback = useCallback((alert: FiredCountdownAlert) => {
    if (alert.vibrate) vibrateCountdownPattern();
    if (alert.sound) void playWebCountdownChime();
    dispatchBrowserCountdownNotification(alert);
    setActiveCountdownAlert(alert);
  }, []);

  useEffect(() => {
    const timers = snoozedTimersRef.current;
    return () => {
      Object.values(timers).forEach((tid) => window.clearTimeout(tid));
    };
  }, []);

  useEffect(() => {
    if (!countdowns.some((cd) => cd.alertEnabled)) return;
    const checkDue = () => {
      const firedSet = new Set(ExamStore.loadFiredCountdownAlerts());
      const dueAlerts = evaluateDueCountdownAlerts(countdowns, firedSet, Date.now());
      for (const alert of dueAlerts) {
        ExamStore.markFiredCountdownAlert(alert.alertKey);
        fireCountdownAlertFeedback(alert);
      }
    };
    checkDue();
    const intervalId = window.setInterval(checkDue, 5000);
    const onVisibilityOrFocus = () => {
      if (document.visibilityState === 'visible') {
        checkDue();
      }
    };
    document.addEventListener('visibilitychange', onVisibilityOrFocus);
    window.addEventListener('focus', onVisibilityOrFocus);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', onVisibilityOrFocus);
      window.removeEventListener('focus', onVisibilityOrFocus);
    };
  }, [countdowns, fireCountdownAlertFeedback]);

  const handleSavePaper = (paper: QuestionPaper, silent: boolean = false) => {
    const normalized = normalizePaperMetadata(paper);
    const nextList = ExamStore.upsertPaper(normalized);
    setPapers(nextList);
    setCurrentPaper(normalized);
    if (!silent) {
      showToast(`Saved "${normalized.metadata.title}" (${normalized.questions.length} Qs)`);
    }
  };

  const handleSelectPaper = (paper: QuestionPaper) => {
    setCurrentPaper(paper);
    ExamStore.setActivePaper(paper);
    showToast(`Loaded "${paper.metadata.title}"`);
  };

  const handleCreatePaper = (paper: QuestionPaper) => {
    const normalized = normalizePaperMetadata(paper);
    const nextList = ExamStore.upsertPaper(normalized);
    setPapers(nextList);
    setCurrentPaper(normalized);
    setShowCreateModal(false);
    setCreateInitialText('');
    navigateTab('master');
    showToast(`Created "${normalized.metadata.title}" (${normalized.questions.length} Qs)`);
  };

  const handleBatchImport = (importedList: QuestionPaper[]) => {
    if (importedList.length === 0) return;
    let list = ExamStore.loadPapers();
    const normalizedItems = importedList.map(normalizePaperMetadata);
    for (const item of normalizedItems) {
      const idx = list.findIndex((p) => p.metadata.id === item.metadata.id);
      list = idx >= 0 ? list.map((p, i) => (i === idx ? item : p)) : [item, ...list];
    }
    ExamStore.savePapers(list);
    setPapers(list);
    setCurrentPaper(normalizedItems[0]);
    setShowBatchImportModal(false);
    navigateTab('folders');
    showToast(`Imported ${normalizedItems.length} question paper(s) into Library`);
  };

  const handleCreateFolder = (name: string, color: string, parentId: string | null) => {
    const folder: FolderItem = {
      id: `folder-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      name,
      color,
      parentId,
      createdAt: Date.now(),
    };
    const next = [...folders, folder];
    setFolders(next);
    ExamStore.saveFolders(next);
    showToast(`Created folder "${name}"`);
  };

  const handleDeleteFolder = (folderId: string) => {
    if (folderId === 'default') return;
    const updatedPapers = papers.map((p) =>
      p.metadata.folderId === folderId
        ? { ...p, metadata: { ...p.metadata, folderId: 'default' } }
        : p
    );
    const nextFolders = folders.filter((f) => f.id !== folderId && f.parentId !== folderId);
    setPapers(updatedPapers);
    setFolders(nextFolders);
    ExamStore.savePapers(updatedPapers);
    ExamStore.saveFolders(nextFolders);
    if (currentPaper && currentPaper.metadata.folderId === folderId) {
      setCurrentPaper({
        ...currentPaper,
        metadata: { ...currentPaper.metadata, folderId: 'default' },
      });
    }
    showToast('Folder removed; papers moved to root folder');
  };

  const handleMovePapersToFolder = (paperIds: string[], targetFolderId: string) => {
    const idSet = new Set(paperIds);
    const next = papers.map((p) =>
      idSet.has(p.metadata.id)
        ? { ...p, metadata: { ...p.metadata, folderId: targetFolderId } }
        : p
    );
    setPapers(next);
    ExamStore.savePapers(next);
    if (currentPaper && idSet.has(currentPaper.metadata.id)) {
      setCurrentPaper({
        ...currentPaper,
        metadata: { ...currentPaper.metadata, folderId: targetFolderId },
      });
    }
    showToast(`Moved ${paperIds.length} paper(s)`);
  };

  const handleDeletePapers = (paperIds: string[]) => {
    const idSet = new Set(paperIds);
    const remaining = papers.filter((p) => !idSet.has(p.metadata.id));
    setPapers(remaining);
    ExamStore.savePapers(remaining);
    if (currentPaper && idSet.has(currentPaper.metadata.id)) {
      setCurrentPaper(remaining[0] || null);
    }
    showToast(`Deleted ${paperIds.length} paper(s)`);
  };

  const handleTogglePaperCompleted = (paperId: string) => {
    const next = papers.map((p) =>
      p.metadata.id === paperId
        ? {
            ...p,
            metadata: {
              ...p.metadata,
              isCompleted: !p.metadata.isCompleted,
              completedAt: !p.metadata.isCompleted ? Date.now() : null,
            },
          }
        : p
    );
    setPapers(next);
    ExamStore.savePapers(next);
    if (currentPaper) {
      const updatedActive = next.find((p) => p.metadata.id === currentPaper.metadata.id);
      if (updatedActive) setCurrentPaper(updatedActive);
    }
  };

  const handleSaveDefaultCountdownLeadMinutes = (minutes: number) => {
    const saved = ExamStore.saveDefaultCountdownLeadMinutes(minutes);
    setDefaultCountdownLeadMinutes(saved);
    showToast(`Default early alert set to ${formatLeadDuration(saved)} before target`);
  };

  const handleSaveCountdowns = (next: ExamCountdownTarget[]) => {
    const prevById = new Map(countdowns.map((c) => [c.id, c]));
    const nextIds = new Set(next.map((c) => c.id));
    for (const prev of countdowns) {
      const updated = next.find((c) => c.id === prev.id);
      if (!nextIds.has(prev.id) || (updated && JSON.stringify(updated) !== JSON.stringify(prev))) {
        ExamStore.clearFiredCountdownAlertsForId(prev.id);
        const pendingTimer = snoozedTimersRef.current[prev.id];
        if (pendingTimer) {
          window.clearTimeout(pendingTimer);
          delete snoozedTimersRef.current[prev.id];
        }
      }
    }
    for (const item of next) {
      if (!prevById.has(item.id)) {
        ExamStore.clearFiredCountdownAlertsForId(item.id);
      }
    }
    setCountdowns(next);
    ExamStore.saveCountdowns(next);
  };

  const handleSnoozeCountdownAlert = (alert: FiredCountdownAlert, minutes: number = 10) => {
    setActiveCountdownAlert(null);
    const existingTimer = snoozedTimersRef.current[alert.countdownId];
    if (existingTimer) {
      window.clearTimeout(existingTimer);
    }
    snoozedTimersRef.current[alert.countdownId] = window.setTimeout(() => {
      delete snoozedTimersRef.current[alert.countdownId];
      fireCountdownAlertFeedback({
        ...alert,
        alertKey: `${alert.countdownId}|SNOOZE|${Date.now()}`,
        stage: 'SNOOZE',
        leadMinutes: minutes,
      });
    }, minutes * 60_000);
    showToast(`Snoozed "${alert.title}" for ${minutes}m`);
  };

  const handleSaveAttempt = (record: AttemptRecord) => {
    const next = ExamStore.saveAttempt(record);
    setAttempts(next);
    const updatedAnalytics = ExamStore.recordTestAttemptAnalytics(record);
    setDailyAnalytics(updatedAnalytics);
    showToast(`Saved attempt score: ${record.score.toFixed(2)} / ${record.maxScore}`);
  };

  const handleDeleteAttempt = (attemptId: string) => {
    const next = ExamStore.deleteAttempt(attemptId);
    setAttempts(next);
  };

  // Phase 4 Handlers
  const ensureCourseRegisteredForNotes = useCallback((notesToCheck: StudyNoteRecord[]) => {
    setKnowledgeCourses((prev) => {
      let changed = false;
      const nextMap: KnowledgeCoursesMap = { ...prev };
      for (const note of notesToCheck) {
        const cTrim = (note.course || '').trim();
        const sTrim = note.subject.trim();
        if (!cTrim) continue;
        const existingKey =
          Object.keys(nextMap).find((k) => k.trim().toLowerCase() === cTrim.toLowerCase()) || cTrim;
        const existingSubs = nextMap[existingKey] || [];
        if (!nextMap[existingKey]) {
          nextMap[existingKey] = sTrim ? [sTrim] : [];
          changed = true;
        } else if (
          sTrim &&
          !existingSubs.some((s) => s.trim().toLowerCase() === sTrim.toLowerCase())
        ) {
          nextMap[existingKey] = [...existingSubs, sTrim].sort((a, b) => a.localeCompare(b));
          changed = true;
        }
      }
      if (!changed) return prev;
      ExamStore.saveKnowledgeCoursesMap(nextMap);
      return nextMap;
    });
  }, []);

  const handleSaveKnowledgeCourses = (nextMap: KnowledgeCoursesMap) => {
    setKnowledgeCourses(nextMap);
    ExamStore.saveKnowledgeCoursesMap(nextMap);
  };

  const handleSaveStudyNote = (note: StudyNoteRecord) => {
    const idx = studyNotes.findIndex((n) => n.id === note.id);
    const next = idx >= 0 ? studyNotes.map((n, i) => (i === idx ? note : n)) : [note, ...studyNotes];
    setStudyNotes(next);
    ExamStore.saveStudyNotes(next);
    ensureCourseRegisteredForNotes([note]);
    setShowStudyNoteModal(false);
    setEditingStudyNote(null);
    setStudyNotePrefill(null);
    showToast(`Saved Study Note "${note.title}"`);
  };

  const handleSaveStudyNotesBatch = (nextNotes: StudyNoteRecord[], toastMsg?: string) => {
    setStudyNotes(nextNotes);
    ExamStore.saveStudyNotes(nextNotes);
    ensureCourseRegisteredForNotes(nextNotes);
    if (toastMsg) showToast(toastMsg);
  };

  const handleDeleteStudyNote = (noteId: string) => {
    const next = studyNotes.filter((n) => n.id !== noteId);
    setStudyNotes(next);
    ExamStore.saveStudyNotes(next);
    showToast('Study note removed');
  };

  const handleUpdateVocabDecks = (next: VocabularyDeck[]) => {
    setVocabDecks(next);
    ExamStore.saveVocabDecks(next);
  };

  const handleUpdateVocabSrs = (next: Record<string, Record<string, FlashcardSrsState>>) => {
    setVocabSrs(next);
    ExamStore.saveVocabSrs(next);
  };

  const handleChangeEnglishVariant = (vr: string) => {
    setEnglishVariant(vr);
    ExamStore.saveEnglishVariant(vr);
    showToast(`Default English variant set to ${vr}`);
  };

  const handleSaveWotdStates = (next: Record<string, LanguageWordOfTheDayState>) => {
    setWotdStates(next);
    ExamStore.saveWotdStates(next);
  };

  const handleSavePromptPresets = (next: PromptPreset[]) => {
    setPromptPresets(next);
    ExamStore.savePromptPresets(next);
  };

  const handleSaveDictationConfig = (cfg: DictationConfig) => {
    setDictationConfig(cfg);
    ExamStore.saveDictationConfig(cfg);
  };

  // Phase 5 Handlers & Workspace Snapshot State
  const handleSaveTodos = (next: StudyTodoItem[]) => {
    setStudyTodos(next);
    ExamStore.saveStudyTodos(next);
  };

  const handleSavePomodoroConfig = (cfg: PomodoroConfig) => {
    setPomodoroConfig(cfg);
    ExamStore.savePomodoroConfig(cfg);
  };

  const handleRecordStudySeconds = (seconds: number) => {
    const next = ExamStore.recordStudyTimeSeconds(seconds);
    setDailyAnalytics(next);
  };

  const handleSaveBrainLabStats = (next: BrainLabStats) => {
    setBrainLabStats(next);
    ExamStore.saveBrainLabStats(next);
  };

  const handleSaveBrainLabSettings = (next: BrainLabSettings) => {
    setBrainLabSettings(next);
    ExamStore.saveBrainLabSettings(next);
  };

  const workspaceBackupState: WorkspaceBackupState = useMemo(
    () => ({
      papers,
      folders,
      attempts,
      activeSessions: ExamStore.getAllActiveSessions(),
      countdowns,
      studyNotes,
      knowledgeCourses,
      vocabDecks,
      vocabSrs,
      englishVariant,
      headwordInbox,
      dismissedHeadwords,
      wotdStates,
      promptPresets,
      dictationConfig,
      studyTodos,
      pomodoroConfig,
      dailyAnalytics,
      brainLabStats,
      brainLabSettings,
      dismissedTips,
      themeMode,
      colorPreset: preset,
      fontScaleMode,
    }),
    [
      papers,
      folders,
      attempts,
      countdowns,
      studyNotes,
      knowledgeCourses,
      vocabDecks,
      vocabSrs,
      englishVariant,
      headwordInbox,
      dismissedHeadwords,
      wotdStates,
      promptPresets,
      dictationConfig,
      studyTodos,
      pomodoroConfig,
      dailyAnalytics,
      brainLabStats,
      brainLabSettings,
      dismissedTips,
      themeMode,
      preset,
      fontScaleMode,
    ]
  );

  const handleChangeAutoBackupSchedule = (schedule: AutoBackupSchedule) => {
    setAutoBackupSchedule(schedule);
    ExamStore.saveAutoBackupSchedule(schedule);
    const labelMap: Record<AutoBackupSchedule, string> = {
      OFF: 'Manual Only',
      ON_CHANGE: 'On Workspace Change',
      EVERY_6H: 'Every 6 Hours',
      DAILY: 'Daily Backup',
    };
    showToast(`Auto-backup schedule set to ${labelMap[schedule]}`);
  };

  const lastAutoBackupAtRef = useRef(lastAutoBackupAt);
  lastAutoBackupAtRef.current = lastAutoBackupAt;

  const workspaceBackupStateRef = useRef(workspaceBackupState);
  workspaceBackupStateRef.current = workspaceBackupState;

  const workspaceBackupSignature = useMemo(
    () => JSON.stringify(workspaceBackupState),
    [workspaceBackupState]
  );
  const lastBackedUpSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    if (autoBackupSchedule === 'OFF') {
      lastBackedUpSignatureRef.current = workspaceBackupSignature;
      return;
    }

    const runSilentAutoGuard = async (signatureAtRun: string) => {
      try {
        const { document, rawJson } = buildVaultArchive(
          workspaceBackupStateRef.current,
          'Auto-Backup Guard'
        );
        const snap: VaultSnapshotRecord = {
          id: `snap-auto-${Date.now()}`,
          label: `Auto Backup (${document.manifest.counts.papersCount} Sets, ${document.manifest.counts.notesCount} Notes)`,
          kind: 'pre_restore',
          createdAt: Date.now(),
          manifest: document.manifest,
          rawJson,
          sizeBytes: rawJson.length,
        };
        const next = await ExamStore.addVaultSnapshotWithBlob(snap);
        setVaultSnapshots(next);

        const now = Date.now();
        lastAutoBackupAtRef.current = now;
        lastBackedUpSignatureRef.current = signatureAtRun;
        setLastAutoBackupAt(now);
        ExamStore.saveLastAutoBackupAt(now);

        const disk = await CloudSyncEngine.getLinkedDiskStatus();
        if (disk.isLinked && disk.permissionState === 'granted') {
          await CloudSyncEngine.syncToLinkedDiskFile(rawJson, undefined, true);
        }
      } catch {
        // Silent guard must not disrupt active study or exam session
      }
    };

    if (autoBackupSchedule === 'ON_CHANGE') {
      if (lastBackedUpSignatureRef.current === null) {
        lastBackedUpSignatureRef.current = workspaceBackupSignature;
        return;
      }
      if (lastBackedUpSignatureRef.current === workspaceBackupSignature) {
        return;
      }
      const elapsed = Date.now() - lastAutoBackupAtRef.current;
      const delayMs = elapsed >= 60_000 ? 15_000 : Math.max(15_000, 60_000 - elapsed);
      const t = setTimeout(() => {
        void runSilentAutoGuard(workspaceBackupSignature);
      }, delayMs);
      return () => clearTimeout(t);
    }

    const cadenceMs =
      autoBackupSchedule === 'EVERY_6H' ? 6 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000;
    const checkTimer = setInterval(() => {
      if (Date.now() - ExamStore.loadLastAutoBackupAt() >= cadenceMs) {
        void runSilentAutoGuard(workspaceBackupSignature);
      }
    }, 60_000);
    return () => clearInterval(checkTimer);
  }, [autoBackupSchedule, workspaceBackupSignature]);

  const handleCreateSnapshot = async (
    label: string,
    kind: 'manual' | 'pre_restore',
    rawJson: string
  ) => {
    let manifest: BackupManifest;
    try {
      const parsed = JSON.parse(rawJson) as { manifest?: BackupManifest };
      manifest = parsed.manifest || buildVaultArchive(workspaceBackupState, 'ExamSet Browser Vault').document.manifest;
    } catch {
      manifest = buildVaultArchive(workspaceBackupState, 'ExamSet Browser Vault').document.manifest;
    }
    const snap: VaultSnapshotRecord = {
      id: `snap-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      label,
      kind,
      createdAt: Date.now(),
      manifest,
      rawJson,
      sizeBytes: rawJson.length,
    };
    const next = await ExamStore.addVaultSnapshotWithBlob(snap);
    setVaultSnapshots(next);
  };

  const handleDeleteSnapshot = async (snapshotId: string) => {
    const next = await ExamStore.deleteVaultSnapshotWithBlob(snapshotId);
    setVaultSnapshots(next);
    showToast('Deleted local vault snapshot');
  };

  const handleExecuteRestore = async (preview: RestorePreview, mode: RestoreMode) => {
    if (mode === 'FULL_REPLACE') {
      try {
        const preArchive = buildVaultArchive(workspaceBackupState, 'Pre-Replace Safety Guard');
        await handleCreateSnapshot('Auto Guard (Pre-Replace)', 'pre_restore', preArchive.rawJson);
      } catch (err) {
        showToast(
          err instanceof Error
            ? `Pre-restore snapshot skipped: ${err.message}`
            : 'Pre-restore snapshot skipped due to storage limit.'
        );
      }
    }

    const merged = applyVaultRestoreToState(
      workspaceBackupState,
      preview,
      mode,
      DEFAULT_ROOT_FOLDER
    );

    const safePapers = merged.papers;
    setPapers(safePapers);
    ExamStore.savePapers(safePapers);

    const activeMatch =
      (currentPaper ? safePapers.find((p) => p.metadata.id === currentPaper.metadata.id) : null) ||
      safePapers[0] ||
      null;
    setCurrentPaper(activeMatch);
    ExamStore.setActivePaper(activeMatch);

    setFolders(merged.folders);
    ExamStore.saveFolders(merged.folders);

    setAttempts(merged.attempts);
    ExamStore.saveAttempts(merged.attempts);
    ExamStore.saveAllActiveSessions(merged.activeSessions || {});

    setCountdowns(merged.countdowns);
    ExamStore.saveCountdowns(merged.countdowns);

    setStudyNotes(merged.studyNotes);
    ExamStore.saveStudyNotes(merged.studyNotes);

    const restoredCourses = merged.knowledgeCourses || {};
    setKnowledgeCourses(restoredCourses);
    ExamStore.saveKnowledgeCoursesMap(restoredCourses);

    setVocabDecks(merged.vocabDecks);
    ExamStore.saveVocabDecks(merged.vocabDecks);

    setVocabSrs(merged.vocabSrs);
    ExamStore.saveVocabSrs(merged.vocabSrs);

    setEnglishVariant(merged.englishVariant);
    ExamStore.saveEnglishVariant(merged.englishVariant);

    setHeadwordInbox(merged.headwordInbox);
    ExamStore.saveHeadwordInbox(merged.headwordInbox);

    setDismissedHeadwords(merged.dismissedHeadwords);
    ExamStore.saveDismissedHeadwords(merged.dismissedHeadwords);

    setWotdStates(merged.wotdStates);
    ExamStore.saveWotdStates(merged.wotdStates);

    setPromptPresets(merged.promptPresets);
    ExamStore.savePromptPresets(merged.promptPresets);

    setDictationConfig(merged.dictationConfig);
    ExamStore.saveDictationConfig(merged.dictationConfig);

    setStudyTodos(merged.studyTodos);
    ExamStore.saveStudyTodos(merged.studyTodos);

    setPomodoroConfig(merged.pomodoroConfig);
    ExamStore.savePomodoroConfig(merged.pomodoroConfig);

    setDailyAnalytics(merged.dailyAnalytics);
    ExamStore.saveDailyAnalytics(merged.dailyAnalytics);

    setBrainLabStats(merged.brainLabStats);
    ExamStore.saveBrainLabStats(merged.brainLabStats);

    setBrainLabSettings(merged.brainLabSettings);
    ExamStore.saveBrainLabSettings(merged.brainLabSettings);

    setDismissedTips(merged.dismissedTips);
    ExamStore.saveDismissedTips(merged.dismissedTips);

    if (mode === 'FULL_REPLACE') {
      setThemeMode(merged.themeMode);
      setPreset(merged.colorPreset);
      setFontScaleMode(merged.fontScaleMode);
    }

    showToast(
      mode === 'SMART_MERGE'
        ? `Smart Merged "${preview.sourceLabel}" cleanly`
        : `Clean Replaced library with "${preview.sourceLabel}"`
    );
  };

  const handleJumpToSuggestion = (sugg: StudyDueSuggestion) => {
    if (sugg.category === 'PAUSED_TEST' && sugg.paperId) {
      const target = papers.find((p) => p.metadata.id === sugg.paperId);
      if (target) {
        setCurrentPaper(target);
        navigateTab('student_portal');
      }
    } else if (sugg.category === 'FLASHCARDS') {
      if (sugg.noteId) setRequestedStudyNoteId(sugg.noteId);
      navigateTab('knowledge_studio');
    } else if (sugg.category === 'VOCABULARY') {
      navigateTab('vocabulary_hub');
    } else if (sugg.category === 'HEADWORD_INBOX') {
      navigateTab('vocabulary_hub');
      setShowHeadwordInboxModal(true);
    }
  };

  const handleJumpToTodoLink = (todo: StudyTodoItem) => {
    if (todo.linkedType === 'PAPER' && todo.linkedPaperId) {
      const p = papers.find((x) => x.metadata.id === todo.linkedPaperId);
      if (p) {
        setCurrentPaper(p);
        navigateTab('student_portal');
      }
    } else if (todo.linkedType === 'NOTE') {
      if (todo.linkedNoteId) setRequestedStudyNoteId(todo.linkedNoteId);
      navigateTab('knowledge_studio');
    } else if (todo.linkedType === 'VOCAB_DECK') {
      navigateTab('vocabulary_hub');
    }
  };

  const handleGuideNavigate = (dest: GuideDestination) => {
    switch (dest) {
      case 'CREATOR':
        navigateTab('creator');
        break;
      case 'MASTER_VIEW':
        navigateTab('master');
        break;
      case 'PRINT_OMR':
        navigateTab('print');
        break;
      case 'STUDENT_TEST':
        navigateTab('student_portal');
        break;
      case 'FOLDERS':
        navigateTab('folders');
        break;
      case 'KNOWLEDGE_STUDIO':
        navigateTab('knowledge_studio');
        break;
      case 'DICTATION_LAB':
        navigateTab('dictation');
        break;
      case 'VOCABULARY_HUB':
        navigateTab('vocabulary_hub');
        break;
      case 'HEADWORD_INBOX':
        navigateTab('vocabulary_hub');
        setShowHeadwordInboxModal(true);
        break;
      case 'AI_PROMPT_STUDIO':
        navigateTab('prompt_generator');
        break;
      case 'STUDY_PLANNER':
        navigateTab('study_planner');
        break;
      case 'BRAIN_LAB':
        navigateTab('brain_lab');
        break;
      case 'BACKUP_VAULT':
        navigateTab('backup_vault');
        break;
      case 'COUNTDOWNS':
        setShowCountdownsModal(true);
        break;
    }
  };

  const hasOpenGlobalModal = Boolean(
    showAppGuideModal ||
      showHeadwordInboxModal ||
      auditVocabDeck ||
      showVocabImportModal ||
      showStudyNoteModal ||
      showCountdownsModal ||
      showAuditModal ||
      editingMetadataPaper ||
      showBatchImportModal ||
      showCreateModal ||
      showFontModal ||
      showThemeModal ||
      mobileMenuOpen
  );

  const canGoBackInApp = hasOpenGlobalModal || tabHasSubBack || tabHistory.length > 0;

  const closeTopGlobalModal = useCallback((): boolean => {
    if (showAppGuideModal) {
      setShowAppGuideModal(false);
      return true;
    }
    if (showHeadwordInboxModal) {
      setShowHeadwordInboxModal(false);
      return true;
    }
    if (auditVocabDeck) {
      setAuditVocabDeck(null);
      return true;
    }
    if (showVocabImportModal) {
      setShowVocabImportModal(false);
      return true;
    }
    if (showStudyNoteModal) {
      if (studyNoteRequestCloseRef.current) {
        studyNoteRequestCloseRef.current();
      } else {
        setShowStudyNoteModal(false);
        setEditingStudyNote(null);
        setStudyNotePrefill(null);
      }
      return true;
    }
    if (showCountdownsModal) {
      setShowCountdownsModal(false);
      return true;
    }
    if (showAuditModal) {
      setShowAuditModal(false);
      return true;
    }
    if (editingMetadataPaper) {
      setEditingMetadataPaper(null);
      return true;
    }
    if (showBatchImportModal) {
      setShowBatchImportModal(false);
      return true;
    }
    if (showCreateModal) {
      setShowCreateModal(false);
      setCreateInitialText('');
      return true;
    }
    if (showFontModal) {
      setShowFontModal(false);
      return true;
    }
    if (showThemeModal) {
      setShowThemeModal(false);
      return true;
    }
    if (mobileMenuOpen) {
      setMobileMenuOpen(false);
      return true;
    }
    return false;
  }, [
    showAppGuideModal,
    showHeadwordInboxModal,
    auditVocabDeck,
    showVocabImportModal,
    showStudyNoteModal,
    showCountdownsModal,
    showAuditModal,
    editingMetadataPaper,
    showBatchImportModal,
    showCreateModal,
    showFontModal,
    showThemeModal,
    mobileMenuOpen,
  ]);

  const handleBackStep = useCallback(
    (allowTabPop: boolean = true): boolean => {
      if (closeTopGlobalModal()) return true;
      if (tabBackHandlerRef.current && tabBackHandlerRef.current()) {
        return true;
      }
      if (allowTabPop && tabHistory.length > 0) {
        const prevTab = tabHistory[tabHistory.length - 1];
        setTabHistory((hist) => hist.slice(0, -1));
        setActiveTab(prevTab);
        return true;
      }
      return false;
    },
    [closeTopGlobalModal, tabHistory]
  );

  const handleBackStepRef = useRef(handleBackStep);
  handleBackStepRef.current = handleBackStep;
  const canGoBackRef = useRef(canGoBackInApp);
  canGoBackRef.current = canGoBackInApp;

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (canGoBackInApp && !hasPushedTrapRef.current) {
      try {
        if (!window.location.search.includes('code=')) {
          window.history.pushState({ examsetTrap: true }, '');
          hasPushedTrapRef.current = true;
        }
      } catch {
        // Ignore restricted history API contexts
      }
    }
  }, [canGoBackInApp]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onPopState = () => {
      hasPushedTrapRef.current = false;
      if (canGoBackRef.current) {
        handleBackStepRef.current(true);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' || e.defaultPrevented) return;
      if (hasOpenGlobalModal || tabBackHandlerRef.current) {
        if (handleBackStepRef.current(false)) {
          e.preventDefault();
        }
      }
    };
    window.addEventListener('popstate', onPopState);
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('popstate', onPopState);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [hasOpenGlobalModal]);

  const totalVocabWords = useMemo(
    () => vocabDecks.reduce((sum, d) => sum + d.entries.length, 0),
    [vocabDecks]
  );

  const openTodoCount = useMemo(
    () => studyTodos.filter((t) => !t.isCompleted).length,
    [studyTodos]
  );

  const navItems: {
    id: ActiveTab;
    label: string;
    shortLabel: string;
    icon: React.ReactNode;
    badge?: string;
    group: 'exam' | 'study' | 'focus';
  }[] = useMemo(
    () => [
      { id: 'creator', label: 'Exam Workbench', shortLabel: 'Workbench', icon: <FileText className="w-4 h-4 flex-shrink-0" />, group: 'exam' },
      { id: 'master', label: 'Master View', shortLabel: 'Master', icon: <Eye className="w-4 h-4 flex-shrink-0" />, badge: currentPaper ? `${currentPaper.questions.length}` : undefined, group: 'exam' },
      { id: 'print', label: 'Print & OMR Studio', shortLabel: 'Print/OMR', icon: <Printer className="w-4 h-4 flex-shrink-0" />, group: 'exam' },
      { id: 'student_portal', label: 'Student Test Portal', shortLabel: 'Test', icon: <CheckCircle2 className="w-4 h-4 flex-shrink-0" />, group: 'exam' },
      { id: 'folders', label: 'Library & Folders', shortLabel: 'Library', icon: <Folder className="w-4 h-4 flex-shrink-0" />, badge: papers.length > 0 ? `${papers.length}` : undefined, group: 'exam' },
      { id: 'knowledge_studio', label: 'Knowledge Studio', shortLabel: 'Knowledge', icon: <BookMarked className="w-4 h-4 flex-shrink-0" />, badge: studyNotes.length > 0 ? `${studyNotes.length}` : undefined, group: 'study' },
      { id: 'dictation', label: 'Dictation Lab', shortLabel: 'Dictation', icon: <Mic className="w-4 h-4 flex-shrink-0" />, group: 'study' },
      { id: 'vocabulary_hub', label: 'Vocabulary Hub', shortLabel: 'Vocab', icon: <Globe className="w-4 h-4 flex-shrink-0" />, badge: totalVocabWords > 0 ? `${totalVocabWords}` : undefined, group: 'study' },
      { id: 'prompt_generator', label: 'AI Prompt Studio', shortLabel: 'Prompts', icon: <Sparkles className="w-4 h-4 flex-shrink-0" />, group: 'study' },
      { id: 'study_planner', label: 'Study Planner', shortLabel: 'Planner', icon: <CalendarCheck className="w-4 h-4 flex-shrink-0" />, badge: openTodoCount > 0 ? `${openTodoCount}` : undefined, group: 'focus' },
      { id: 'brain_lab', label: 'Brain & Calm Lab', shortLabel: 'Brain Lab', icon: <Brain className="w-4 h-4 flex-shrink-0" />, group: 'focus' },
      { id: 'backup_vault', label: 'Backup Vault', shortLabel: 'Vault', icon: <Archive className="w-4 h-4 flex-shrink-0" />, badge: vaultSnapshots.length > 0 ? `${vaultSnapshots.length}` : undefined, group: 'focus' },
      { id: 'share', label: 'Share & Export', shortLabel: 'Share', icon: <Share2 className="w-4 h-4 flex-shrink-0" />, group: 'focus' },
    ],
    [currentPaper, papers.length, studyNotes.length, totalVocabWords, openTodoCount, vaultSnapshots.length]
  );

  const navGroupLabels: Record<'exam' | 'study' | 'focus', string> = {
    exam: 'Assessment',
    study: 'Study & Languages',
    focus: 'Focus & Backup',
  };

  return (
    <div
      className="min-h-[100dvh] flex flex-col font-sans transition-colors duration-200 overflow-x-hidden"
      style={{ backgroundColor: colors.bg, color: colors.ink }}
    >
      {/* Toast Notification (Positioned above Mobile Bottom Nav) */}
      {notification && (
        <div
          className="fixed bottom-20 md:bottom-5 right-3 sm:right-5 left-3 sm:left-auto z-50 flex items-center gap-2 px-4 py-2.5 rounded-xl shadow-2xl border max-w-md"
          style={{
            backgroundColor: colors.navyBanner,
            color: colors.navyBannerText,
            borderColor: colors.teal,
          }}
        >
          <Bell className="w-4 h-4 text-emerald-300 flex-shrink-0" />
          <span className="text-sm font-semibold truncate">{notification}</span>
        </div>
      )}

      {/* Top Sticky Header */}
      <header
        className="sticky top-0 z-40 border-b backdrop-blur-md"
        style={{
          backgroundColor: colors.surface,
          borderColor: colors.border,
        }}
      >
        <div className="max-w-[1600px] mx-auto px-3 sm:px-5 lg:px-6">
          <div className="flex items-center justify-between min-h-[3.5rem] py-1.5 gap-2 sm:gap-3">
            {/* Brand + Active Set Summary */}
            <div className="flex items-center gap-2 sm:gap-3 min-w-0 flex-1">
              {canGoBackInApp && (
                <button
                  type="button"
                  onClick={() => handleBackStep(true)}
                  className="min-h-[38px] px-2.5 py-1.5 flex items-center gap-1 rounded-xl border text-xs font-bold transition flex-shrink-0"
                  style={{ backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }}
                  title="Step Back (or press browser Back / Esc)"
                  aria-label="Go back to previous view"
                >
                  <ArrowLeft className="w-4 h-4" />
                  <span className="hidden sm:inline">Back</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => setRailExpanded((v) => !v)}
                className="hidden lg:flex min-w-[40px] min-h-[40px] items-center justify-center rounded-xl border transition flex-shrink-0"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                title={railExpanded ? 'Collapse Sidebar Rail' : 'Expand Sidebar Rail'}
                aria-label="Toggle workspace navigation rail"
              >
                {railExpanded ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
              </button>
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center font-black shadow-sm flex-shrink-0"
                style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
              >
                <BookOpen className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 min-w-0">
                  <h1 className="text-base sm:text-lg font-black tracking-tight flex-shrink-0" style={{ color: colors.inkBold }}>
                    ExamSet
                  </h1>
                  <span
                    className="hidden sm:inline-block text-[0.625rem] px-2.5 py-0.5 rounded-full font-bold border truncate"
                    style={{
                      backgroundColor: colors.tealLight,
                      color: colors.tealDark,
                      borderColor: colors.border,
                    }}
                  >
                    Set {currentPaper ? currentPaper.metadata.setNumber || '01' : '--'} • A4 OMR • SM-2
                  </span>
                </div>
                <p
                  className="es-scroll-x text-[0.6875rem] font-medium"
                  style={{ color: colors.muted }}
                  title={
                    currentPaper
                      ? `${currentPaper.metadata.title} (${currentPaper.questions.length} Qs)`
                      : 'Clean workspace — Create or Import a Question Set or Restore Vault'
                  }
                >
                  {currentPaper ? (
                    <>
                      {currentPaper.metadata.title}{' '}
                      <span className="tabular-nums opacity-85">({currentPaper.questions.length} Qs)</span>
                    </>
                  ) : (
                    'Clean Workspace • Ready to Create, Import, or Restore'
                  )}
                </p>
              </div>
            </div>

            {/* Theme / Typography / App Guide Controls + Mobile Toggle */}
            <div className="flex items-center gap-1.5 flex-shrink-0">
              <button
                type="button"
                onClick={() => setShowAppGuideModal(true)}
                className="min-h-[40px] flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-bold transition"
                style={{ backgroundColor: colors.tealLight, borderColor: colors.border, color: colors.tealDark }}
                title="App Guide & Feature Finder"
              >
                <HelpCircle className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
                <span className="hidden sm:inline">Guide</span>
              </button>

              <button
                type="button"
                onClick={() => setShowThemeModal(true)}
                className="min-h-[40px] flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-bold transition"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                title="Color Scheme & Light/Dark Mode"
              >
                <Palette className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
                <span className="hidden md:inline">{isDark ? 'Dark' : 'Light'}</span>
              </button>

              <button
                type="button"
                onClick={() => setShowFontModal(true)}
                className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg border transition"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                title="Typography & System Scaling"
                aria-label="Typography & System Scaling"
              >
                <Type className="w-4 h-4" style={{ color: colors.teal }} />
              </button>

              <button
                type="button"
                onClick={() => setMobileMenuOpen((o) => !o)}
                className="lg:hidden min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                aria-label="Toggle Full Navigation"
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
            </div>
          </div>

          {/* Tablet Portrait (md only) Horizontal Scrollable Navigation Strip */}
          <nav
            className="hidden md:flex lg:hidden items-center gap-1 pb-2 pt-1 overflow-x-auto border-t"
            style={{ borderColor: colors.borderLight }}
          >
            {navItems.map((item, idx) => {
              const isActive = activeTab === item.id;
              const prevGroup = idx > 0 ? navItems[idx - 1].group : item.group;
              return (
                <React.Fragment key={item.id}>
                  {idx > 0 && item.group !== prevGroup && (
                    <span
                      className="h-5 w-px mx-1 flex-shrink-0"
                      style={{ backgroundColor: colors.border }}
                    />
                  )}
                  <button
                    type="button"
                    onClick={() => navigateTab(item.id)}
                    className="min-h-[38px] flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition flex-shrink-0"
                    style={
                      isActive
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                        : { color: colors.inkMuted, backgroundColor: 'transparent' }
                    }
                  >
                    {item.icon}
                    <span>{item.shortLabel}</span>
                    {item.badge && (
                      <span
                        className="ml-0.5 text-[0.625rem] px-1.5 py-0.2 rounded-full font-bold"
                        style={
                          isActive
                            ? { backgroundColor: 'rgba(255,255,255,0.22)', color: colors.primaryButtonText }
                            : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                        }
                      >
                        {item.badge}
                      </span>
                    )}
                  </button>
                </React.Fragment>
              );
            })}
          </nav>
        </div>

        {/* Mobile / Compact Full Workspace Drawer Menu */}
        {mobileMenuOpen && (
          <div
            className="lg:hidden border-t px-3 py-3 space-y-2 shadow-xl max-h-[70dvh] overflow-y-auto"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            {(['exam', 'study', 'focus'] as const).map((grp) => (
              <div key={grp} className="space-y-1">
                <div
                  className="px-2 pt-1 text-[0.625rem] font-black uppercase tracking-wider"
                  style={{ color: colors.muted }}
                >
                  {navGroupLabels[grp]}
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                  {navItems
                    .filter((item) => item.group === grp)
                    .map((item) => {
                      const isActive = activeTab === item.id;
                      return (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => navigateTab(item.id)}
                          className="w-full min-h-[44px] flex items-center justify-between px-3 py-2 rounded-xl text-xs font-bold transition"
                          style={
                            isActive
                              ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                              : { color: colors.ink, backgroundColor: colors.surfaceVariant }
                          }
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            {item.icon}
                            <span className="truncate">{item.label}</span>
                          </div>
                          {item.badge && (
                            <span className="text-[0.6875rem] px-2 py-0.5 rounded-full bg-black/15 flex-shrink-0">
                              {item.badge}
                            </span>
                          )}
                        </button>
                      );
                    })}
                </div>
              </div>
            ))}
          </div>
        )}
      </header>

      {/* Main Responsive Shell: Desktop/Landscape Navigation Rail + Adaptive Workspace */}
      <div className="flex-1 max-w-[1600px] w-full mx-auto flex gap-4 lg:gap-5 px-2.5 sm:px-5 lg:px-6 pt-3 sm:pt-5 pb-24 md:pb-6">
        {/* Desktop & Landscape Tablet Left Navigation Rail */}
        <aside
          className={`hidden lg:flex flex-col flex-shrink-0 rounded-2xl border p-2.5 self-start sticky top-[4.5rem] max-h-[calc(100dvh-5.5rem)] overflow-y-auto transition-all duration-200 ${
            railExpanded ? 'w-60' : 'w-[72px]'
          }`}
          style={{ backgroundColor: colors.surface, borderColor: colors.border, boxShadow: colors.cardShadow }}
        >
          <div className="space-y-3">
            {(['exam', 'study', 'focus'] as const).map((grp) => (
              <div key={grp} className="space-y-1">
                {railExpanded && (
                  <div
                    className="px-2.5 pt-1 text-[0.625rem] font-black uppercase tracking-wider"
                    style={{ color: colors.muted }}
                  >
                    {navGroupLabels[grp]}
                  </div>
                )}
                {navItems
                  .filter((item) => item.group === grp)
                  .map((item) => {
                    const isActive = activeTab === item.id;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => navigateTab(item.id)}
                        title={item.label}
                        className={`w-full min-h-[42px] flex items-center rounded-xl text-xs font-bold transition ${
                          railExpanded ? 'justify-between px-3 py-2 gap-2' : 'justify-center px-2 py-2 relative'
                        }`}
                        style={
                          isActive
                            ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                            : { color: colors.inkMuted, backgroundColor: 'transparent' }
                        }
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          {item.icon}
                          {railExpanded && <span className="truncate">{item.label}</span>}
                        </div>
                        {railExpanded && item.badge && (
                          <span
                            className="text-[0.625rem] px-1.5 py-0.5 rounded-full font-bold flex-shrink-0"
                            style={
                              isActive
                                ? { backgroundColor: 'rgba(255,255,255,0.22)', color: colors.primaryButtonText }
                                : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                            }
                          >
                            {item.badge}
                          </span>
                        )}
                      </button>
                    );
                  })}
              </div>
            ))}
          </div>
        </aside>

        {/* Primary Workspace Card */}
        <main className="flex-1 min-w-0">
          <section
            className="rounded-2xl border p-3.5 sm:p-5 lg:p-6 min-h-[calc(100dvh-10rem)] transition-colors"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, boxShadow: colors.cardShadow }}
          >
          {activeTab === 'creator' && (
            <CreatorTab
              currentPaper={currentPaper}
              onSavePaper={handleSavePaper}
              onNavigateToMaster={() => navigateTab('master')}
              onNavigateToStudentPortal={() => navigateTab('student_portal')}
              onNavigateToPrint={() => navigateTab('print')}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId(currentPaper?.metadata.folderId || 'default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onNavigateToVault={() => navigateTab('backup_vault')}
              onOpenEditMetadata={() => currentPaper && setEditingMetadataPaper(currentPaper)}
              onOpenAuditModal={() => currentPaper && setShowAuditModal(true)}
            />
          )}

          {activeTab === 'master' && (
            <MasterViewTab
              currentPaper={currentPaper}
              onUpdatePaper={handleSavePaper}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId('default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onNavigateToVault={() => navigateTab('backup_vault')}
              onOpenEditMetadata={() => currentPaper && setEditingMetadataPaper(currentPaper)}
              onOpenAuditModal={() => currentPaper && setShowAuditModal(true)}
              registerBackHandler={registerTabBackHandler}
            />
          )}

          {activeTab === 'print' && (
            <PrintGeneratorTab
              currentPaper={currentPaper}
              allPapers={papers}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId('default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onNavigateToVault={() => navigateTab('backup_vault')}
            />
          )}

          {activeTab === 'student_portal' && (
            <StudentPortalTab
              currentPaper={currentPaper}
              attempts={attempts}
              onSaveAttempt={handleSaveAttempt}
              onDeleteAttempt={handleDeleteAttempt}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId('default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onNavigateToVault={() => navigateTab('backup_vault')}
              registerBackHandler={registerTabBackHandler}
            />
          )}

          {activeTab === 'folders' && (
            <FolderManagerTab
              currentPaper={currentPaper}
              papers={papers}
              folders={folders}
              countdowns={countdowns}
              onSelectPaper={handleSelectPaper}
              onNavigateToMaster={() => navigateTab('master')}
              onNavigateToStudentPortal={() => navigateTab('student_portal')}
              onOpenCreateModal={(folderId) => {
                setCreateDefaultFolderId(folderId || 'default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onOpenEditMetadata={(paper) => setEditingMetadataPaper(paper)}
              onOpenCountdownsModal={() => setShowCountdownsModal(true)}
              onCreateFolder={handleCreateFolder}
              onDeleteFolder={handleDeleteFolder}
              onMovePapersToFolder={handleMovePapersToFolder}
              onDeletePapers={handleDeletePapers}
              onTogglePaperCompleted={handleTogglePaperCompleted}
              studyNotes={studyNotes}
              onOpenKnowledgeNote={(noteId) => {
                setRequestedStudyNoteId(noteId);
                navigateTab('knowledge_studio');
              }}
              registerBackHandler={registerTabBackHandler}
            />
          )}

          {activeTab === 'knowledge_studio' && (
            <KnowledgeStudioTab
              notes={studyNotes}
              papers={papers}
              attempts={attempts}
              folders={folders}
              registeredCoursesMap={knowledgeCourses}
              initialSelectedNoteId={requestedStudyNoteId}
              onClearInitialSelectedNoteId={() => setRequestedStudyNoteId(null)}
              onSaveCoursesMap={handleSaveKnowledgeCourses}
              onSaveNote={handleSaveStudyNote}
              onSaveNotesBatch={handleSaveStudyNotesBatch}
              onDeleteNote={handleDeleteStudyNote}
              onOpenEditor={(note, prefill) => {
                setEditingStudyNote(note || null);
                setStudyNotePrefill(prefill || null);
                setShowStudyNoteModal(true);
              }}
              onSendNoteToDictation={(noteId) => {
                setDictationTargetNoteId(noteId);
                navigateTab('dictation');
              }}
              onNotify={showToast}
              registerBackHandler={registerTabBackHandler}
            />
          )}

          {activeTab === 'dictation' && (
            <NotebookDictationTab
              studyNotes={studyNotes}
              initialNoteId={dictationTargetNoteId}
              dictationConfig={dictationConfig}
              onSaveConfig={handleSaveDictationConfig}
            />
          )}

          {activeTab === 'vocabulary_hub' && (
            <VocabularyHubTab
              decks={vocabDecks}
              srsByDeck={vocabSrs}
              englishVariant={englishVariant}
              headwordInbox={headwordInbox}
              wotdStates={wotdStates}
              onChangeEnglishVariant={handleChangeEnglishVariant}
              onUpdateDecks={handleUpdateVocabDecks}
              onUpdateSrs={handleUpdateVocabSrs}
              onSaveWotdStates={handleSaveWotdStates}
              onOpenImportModal={(lang) => {
                setVocabImportDefaultLang(lang || 'English');
                setShowVocabImportModal(true);
              }}
              onOpenHeadwordInbox={() => setShowHeadwordInboxModal(true)}
              onOpenAuditModal={(deck) => setAuditVocabDeck(deck)}
            />
          )}

          {activeTab === 'prompt_generator' && (
            <PromptGeneratorTab
              presets={promptPresets}
              onSavePresets={handleSavePromptPresets}
              vocabDecks={vocabDecks}
              folders={folders}
              defaultEnglishVariant={englishVariant}
              onNavigateToCreator={() => navigateTab('creator')}
              onNavigateToKnowledge={() => navigateTab('knowledge_studio')}
              onSendToCreator={(promptText) => {
                setCreateDefaultFolderId(currentPaper?.metadata.folderId || 'default');
                setCreateInitialText(promptText);
                setShowCreateModal(true);
              }}
              onCreateStudyNoteFromPrompt={(collectionType, subject, topic) => {
                setEditingStudyNote(null);
                setStudyNotePrefill({ collectionType, subject, topic });
                navigateTab('knowledge_studio');
                setShowStudyNoteModal(true);
              }}
              onOpenVocabImport={() => {
                navigateTab('vocabulary_hub');
                setShowVocabImportModal(true);
              }}
              onOpenHeadwordInbox={() => {
                navigateTab('vocabulary_hub');
                setShowHeadwordInboxModal(true);
              }}
            />
          )}

          {activeTab === 'study_planner' && (
            <StudyPlannerTab
              papers={papers}
              studyNotes={studyNotes}
              vocabDecks={vocabDecks}
              vocabSrs={vocabSrs}
              headwordInbox={headwordInbox}
              activeSessions={ExamStore.getAllActiveSessions()}
              countdowns={countdowns}
              todos={studyTodos}
              pomodoroConfig={pomodoroConfig}
              dailyAnalytics={dailyAnalytics}
              onSaveTodos={handleSaveTodos}
              onSavePomodoroConfig={handleSavePomodoroConfig}
              onRecordFocusSeconds={handleRecordStudySeconds}
              onOpenCountdownsModal={() => setShowCountdownsModal(true)}
              onJumpToSuggestion={handleJumpToSuggestion}
              onJumpToTodoLink={handleJumpToTodoLink}
              onNotify={showToast}
            />
          )}

          {activeTab === 'brain_lab' && (
            <BrainLabTab
              stats={brainLabStats}
              settings={brainLabSettings}
              onSaveStats={handleSaveBrainLabStats}
              onSaveSettings={handleSaveBrainLabSettings}
              onRecordStudySeconds={handleRecordStudySeconds}
              onNotify={showToast}
            />
          )}

          {activeTab === 'backup_vault' && (
            <BackupVaultTab
              workspaceState={workspaceBackupState}
              snapshots={vaultSnapshots}
              autoBackupSchedule={autoBackupSchedule}
              lastAutoBackupAt={lastAutoBackupAt}
              onChangeAutoBackupSchedule={handleChangeAutoBackupSchedule}
              onCreateSnapshot={handleCreateSnapshot}
              onDeleteSnapshot={handleDeleteSnapshot}
              onExecuteRestore={handleExecuteRestore}
              onImportPromptPresets={handleSavePromptPresets}
              onNotify={showToast}
            />
          )}

          {activeTab === 'share' && (
            <ShareSystemTab
              currentPaper={currentPaper}
              allPapers={papers}
              onImportPaper={(paper) => {
                handleSavePaper(paper);
                navigateTab('master');
              }}
              onImportPapers={handleBatchImport}
              onOpenBatchImport={() => setShowBatchImportModal(true)}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId('default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
            />
          )}
          </section>
        </main>
      </div>

      {/* Mobile Bottom Quick-Navigation Bar */}
      <nav
        className="md:hidden fixed bottom-0 inset-x-0 z-40 border-t grid grid-cols-5 backdrop-blur-md"
        style={{
          backgroundColor: colors.surface,
          borderColor: colors.border,
          paddingBottom: 'env(safe-area-inset-bottom, 0px)',
        }}
        aria-label="Mobile quick navigation"
      >
        {[
          { id: 'folders' as const, label: 'Library', icon: <Folder className="w-4 h-4" /> },
          { id: 'master' as const, label: 'Master', icon: <Eye className="w-4 h-4" /> },
          { id: 'student_portal' as const, label: 'Test', icon: <CheckCircle2 className="w-4 h-4" /> },
          { id: 'knowledge_studio' as const, label: 'Study', icon: <BookMarked className="w-4 h-4" /> },
        ].map((item) => {
          const active = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => navigateTab(item.id)}
              className="min-h-[54px] flex flex-col items-center justify-center gap-0.5 text-[0.625rem] font-bold"
              style={{
                color: active ? colors.teal : colors.muted,
                backgroundColor: active ? colors.tealLight : 'transparent',
              }}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          );
        })}
        <button
          type="button"
          onClick={() => setMobileMenuOpen((o) => !o)}
          className="min-h-[54px] flex flex-col items-center justify-center gap-0.5 text-[0.625rem] font-bold"
          style={{
            color: mobileMenuOpen ? colors.teal : colors.muted,
            backgroundColor: mobileMenuOpen ? colors.tealLight : 'transparent',
          }}
        >
          <MoreHorizontal className="w-4 h-4" />
          <span>More</span>
        </button>
      </nav>

      {/* Footer (hidden on small phones to save space above bottom nav) */}
      <footer
        className="hidden md:block border-t py-3 px-4 text-center text-xs"
        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
      >
        ExamSet Suite • A4 OMR • SM-2 &amp; Dictation • Vocabulary Hub • Study Planner &amp; Focus • Brain Lab • Cloud &amp; Disk Backup Vault
      </footer>

      {/* Global Dialogs & Workflow Modals */}
      <ThemePaletteModal isOpen={showThemeModal} onClose={() => setShowThemeModal(false)} />
      <FontScaleModal isOpen={showFontModal} onClose={() => setShowFontModal(false)} />

      {showCreateModal && (
        <InputModal
          isOpen={showCreateModal}
          folders={folders}
          existingPapers={papers}
          defaultFolderId={createDefaultFolderId}
          initialText={createInitialText}
          onCreatePaper={handleCreatePaper}
          onClose={() => {
            setShowCreateModal(false);
            setCreateInitialText('');
          }}
        />
      )}

      {showBatchImportModal && (
        <BatchImportModal
          isOpen={showBatchImportModal}
          folders={folders}
          existingPapers={papers}
          defaultFolderId={currentPaper?.metadata.folderId || 'default'}
          onImportBatch={handleBatchImport}
          onClose={() => setShowBatchImportModal(false)}
        />
      )}

      {editingMetadataPaper && (
        <EditMetadataModal
          isOpen={Boolean(editingMetadataPaper)}
          paper={editingMetadataPaper}
          folders={folders}
          existingPapers={papers}
          onSaveMetadata={(updated) => {
            handleSavePaper(updated);
            setEditingMetadataPaper(null);
          }}
          onClose={() => setEditingMetadataPaper(null)}
        />
      )}

      {showAuditModal && currentPaper && (
        <AuditResolutionModal
          isOpen={showAuditModal}
          paper={currentPaper}
          onUpdatePaper={(updated) => handleSavePaper(updated)}
          onClose={() => setShowAuditModal(false)}
        />
      )}

      {showCountdownsModal && (
        <ExamCountdownsModal
          isOpen={showCountdownsModal}
          countdowns={countdowns}
          defaultLeadMinutes={defaultCountdownLeadMinutes}
          onSaveDefaultLeadMinutes={handleSaveDefaultCountdownLeadMinutes}
          onSaveCountdowns={handleSaveCountdowns}
          onClose={() => setShowCountdownsModal(false)}
        />
      )}

      {activeCountdownAlert && (
        <div
          className="fixed inset-0 z-[70] flex items-center justify-center p-4"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)' }}
          onClick={() => setActiveCountdownAlert(null)}
        >
          <div
            className="w-full max-w-md rounded-2xl shadow-2xl p-5 space-y-3"
            style={{
              backgroundColor: colors.surface,
              border: `2px solid ${colors.teal}`,
              color: colors.ink,
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start gap-3">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                style={{ backgroundColor: colors.tealLight, color: colors.teal }}
              >
                <Bell className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold uppercase tracking-wide" style={{ color: colors.tealDark }}>
                  {activeCountdownAlert.stage === 'EARLY'
                    ? `Countdown Alert — in ${formatLeadDuration(activeCountdownAlert.leadMinutes)}`
                    : activeCountdownAlert.stage === 'ZERO'
                    ? 'Countdown Reached — Starting Now!'
                    : 'Snoozed Countdown Reminder'}
                </div>
                <h3 className="text-base font-extrabold mt-0.5 break-words" style={{ color: colors.inkBold }}>
                  {activeCountdownAlert.title}
                </h3>
              </div>
            </div>

            {activeCountdownAlert.customMessage && (
              <div
                className="p-3 rounded-xl text-sm font-medium border"
                style={{
                  backgroundColor: colors.tealLight,
                  borderColor: colors.teal,
                  color: colors.inkBold,
                }}
              >
                {activeCountdownAlert.customMessage}
              </div>
            )}

            <div className="text-xs" style={{ color: colors.muted }}>
              Scheduled: {activeCountdownAlert.scheduleText}
            </div>

            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => handleSnoozeCountdownAlert(activeCountdownAlert, 10)}
                className="px-4 py-2 rounded-xl text-xs font-bold border"
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderColor: colors.border,
                  color: colors.inkBold,
                }}
              >
                Snooze 10m
              </button>
              <button
                type="button"
                onClick={() => setActiveCountdownAlert(null)}
                className="px-4 py-2 rounded-xl text-xs font-bold"
                style={{
                  backgroundColor: colors.teal,
                  color: colors.onAccentText,
                }}
              >
                Dismiss
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Phase 4 Modals */}
      {showStudyNoteModal && (
        <StudyNoteEditorModal
          isOpen={showStudyNoteModal}
          initialNote={editingStudyNote}
          prefill={studyNotePrefill}
          papers={papers}
          folders={folders}
          registeredCoursesMap={knowledgeCourses}
          existingNotes={studyNotes}
          onSaveNote={handleSaveStudyNote}
          onRegisterCloseRequest={registerStudyNoteCloseRequest}
          onClose={() => {
            setShowStudyNoteModal(false);
            setEditingStudyNote(null);
            setStudyNotePrefill(null);
          }}
        />
      )}

      {showVocabImportModal && (
        <VocabularyImportModal
          isOpen={showVocabImportModal}
          decks={vocabDecks}
          defaultLanguage={vocabImportDefaultLang}
          defaultEnglishVariant={englishVariant}
          onCompleteImport={(updatedDecks, msg) => {
            handleUpdateVocabDecks(updatedDecks);
            setShowVocabImportModal(false);
            showToast(msg);
          }}
          onClose={() => setShowVocabImportModal(false)}
        />
      )}

      {auditVocabDeck && (
        <VocabularyAuditModal
          isOpen={Boolean(auditVocabDeck)}
          deck={vocabDecks.find((d) => d.id === auditVocabDeck.id) || auditVocabDeck}
          allDecks={vocabDecks}
          onUpdateDeck={(updated) => {
            const next = vocabDecks.map((d) => (d.id === updated.id ? updated : d));
            handleUpdateVocabDecks(next);
            setAuditVocabDeck(updated);
            showToast(`Updated audited deck "${updated.title}"`);
          }}
          onClose={() => setAuditVocabDeck(null)}
        />
      )}

      {showHeadwordInboxModal && (
        <HeadwordInboxModal
          isOpen={showHeadwordInboxModal}
          inbox={headwordInbox}
          dismissedKeys={dismissedHeadwords}
          decks={vocabDecks}
          defaultEnglishVariant={englishVariant}
          onSaveInbox={(items) => {
            setHeadwordInbox(items);
            ExamStore.saveHeadwordInbox(items);
          }}
          onDismissKey={(compositeKey, itemId) => {
            const nextInbox = headwordInbox.filter((x) => x.id !== itemId);
            const nextDismissed = Array.from(new Set([...dismissedHeadwords, compositeKey]));
            setHeadwordInbox(nextInbox);
            setDismissedHeadwords(nextDismissed);
            ExamStore.saveHeadwordInbox(nextInbox);
            ExamStore.saveDismissedHeadwords(nextDismissed);
          }}
          onClose={() => setShowHeadwordInboxModal(false)}
        />
      )}
      <AppGuideModal
        isOpen={showAppGuideModal}
        onClose={() => setShowAppGuideModal(false)}
        onNavigateDestination={handleGuideNavigate}
      />
    </div>
  );
};

export const App: React.FC = () => (
  <ThemeProvider>
    <ExamSetWorkspace />
  </ThemeProvider>
);

export default App;
