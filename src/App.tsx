import React, { useEffect, useMemo, useState } from 'react';
import {
  Archive,
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
  LanguageWordOfTheDayState,
  PromptPreset,
  StudyNoteRecord,
  VocabularyDeck,
} from './types/phase4';
import {
  type AutoBackupSchedule,
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
import { STARTER_STUDY_NOTES, STARTER_VOCAB_DECKS } from './data/phase4Seeds';
import { applyVaultRestoreToState, buildVaultArchive, type WorkspaceBackupState } from './utils/vaultArchiveEngine';
import { CloudSyncEngine } from './utils/cloudSyncEngine';
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
    targetExam: 'All India Competitive Exam',
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

  const [papers, setPapers] = useState<QuestionPaper[]>(() => ExamStore.loadPapers(DEFAULT_PAPER));
  const [currentPaper, setCurrentPaper] = useState<QuestionPaper>(() =>
    ExamStore.getActivePaper(DEFAULT_PAPER)
  );
  const [folders, setFolders] = useState<FolderItem[]>(() =>
    ExamStore.loadFolders(ExamStore.loadPapers(DEFAULT_PAPER))
  );
  const [countdowns, setCountdowns] = useState<ExamCountdownTarget[]>(() =>
    ExamStore.loadCountdowns()
  );
  const [attempts, setAttempts] = useState<AttemptRecord[]>(() => ExamStore.loadAttempts());

  // Phase 4 Persistent State
  const [studyNotes, setStudyNotes] = useState<StudyNoteRecord[]>(() =>
    ExamStore.loadStudyNotes(STARTER_STUDY_NOTES)
  );
  const [vocabDecks, setVocabDecks] = useState<VocabularyDeck[]>(() =>
    ExamStore.loadVocabDecks(STARTER_VOCAB_DECKS)
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

  useEffect(() => {
    ExamStore.setActivePaper(currentPaper);
  }, [currentPaper]);

  useEffect(() => {
    void CloudSyncEngine.handleOAuthCallbackIfPresent().then((res) => {
      if (res) {
        showToast(res.message);
        setActiveTab('backup_vault');
      }
    });
  }, []);

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
    setActiveTab('master');
    showToast(`Created "${normalized.metadata.title}" (${normalized.questions.length} Qs)`);
  };

  const handleBatchImport = (importedList: QuestionPaper[]) => {
    if (importedList.length === 0) return;
    let list = ExamStore.loadPapers(DEFAULT_PAPER);
    const normalizedItems = importedList.map(normalizePaperMetadata);
    for (const item of normalizedItems) {
      const idx = list.findIndex((p) => p.metadata.id === item.metadata.id);
      list = idx >= 0 ? list.map((p, i) => (i === idx ? item : p)) : [item, ...list];
    }
    ExamStore.savePapers(list);
    setPapers(list);
    setCurrentPaper(normalizedItems[0]);
    setShowBatchImportModal(false);
    setActiveTab('folders');
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
    if (currentPaper.metadata.folderId === folderId) {
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
    if (idSet.has(currentPaper.metadata.id)) {
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
    const finalList = remaining.length > 0 ? remaining : [normalizePaperMetadata(DEFAULT_PAPER)];
    setPapers(finalList);
    ExamStore.savePapers(finalList);
    if (idSet.has(currentPaper.metadata.id)) {
      setCurrentPaper(finalList[0]);
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
    const updatedActive = next.find((p) => p.metadata.id === currentPaper.metadata.id);
    if (updatedActive) setCurrentPaper(updatedActive);
  };

  const handleSaveCountdowns = (next: ExamCountdownTarget[]) => {
    setCountdowns(next);
    ExamStore.saveCountdowns(next);
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
  const handleSaveStudyNote = (note: StudyNoteRecord) => {
    const idx = studyNotes.findIndex((n) => n.id === note.id);
    const next = idx >= 0 ? studyNotes.map((n, i) => (i === idx ? note : n)) : [note, ...studyNotes];
    setStudyNotes(next);
    ExamStore.saveStudyNotes(next);
    setShowStudyNoteModal(false);
    setEditingStudyNote(null);
    setStudyNotePrefill(null);
    showToast(`Saved Study Note "${note.title}"`);
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
      countdowns,
      studyNotes,
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

  useEffect(() => {
    if (autoBackupSchedule === 'OFF') return;

    const runSilentAutoGuard = async () => {
      try {
        const { document, rawJson } = buildVaultArchive(workspaceBackupState, 'Auto-Backup Guard');
        const snap: VaultSnapshotRecord = {
          id: `snap-auto-${Date.now()}`,
          label: `Auto Backup (${document.manifest.counts.papersCount} Sets, ${document.manifest.counts.notesCount} Notes)`,
          kind: 'pre_restore',
          createdAt: Date.now(),
          manifest: document.manifest,
          rawJson,
        };
        const next = ExamStore.addVaultSnapshot(snap);
        setVaultSnapshots(next);

        const now = Date.now();
        setLastAutoBackupAt(now);
        ExamStore.saveLastAutoBackupAt(now);

        const disk = await CloudSyncEngine.getLinkedDiskStatus();
        if (disk.isLinked && disk.permissionState === 'granted') {
          await CloudSyncEngine.syncToLinkedDiskFile(rawJson);
        }
      } catch {
        // Silent guard must not disrupt active study or exam session
      }
    };

    if (autoBackupSchedule === 'ON_CHANGE') {
      const elapsed = Date.now() - lastAutoBackupAt;
      const delayMs = elapsed >= 60_000 ? 15_000 : Math.max(15_000, 60_000 - elapsed);
      const t = setTimeout(() => {
        void runSilentAutoGuard();
      }, delayMs);
      return () => clearTimeout(t);
    }

    const cadenceMs =
      autoBackupSchedule === 'EVERY_6H' ? 6 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000;
    const checkTimer = setInterval(() => {
      if (Date.now() - ExamStore.loadLastAutoBackupAt() >= cadenceMs) {
        void runSilentAutoGuard();
      }
    }, 60_000);
    return () => clearInterval(checkTimer);
  }, [autoBackupSchedule, lastAutoBackupAt, workspaceBackupState]);

  const handleCreateSnapshot = (label: string, kind: 'manual' | 'pre_restore', rawJson: string) => {
    const { document } = buildVaultArchive(workspaceBackupState, 'ExamSet Browser Vault');
    const snap: VaultSnapshotRecord = {
      id: `snap-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      label,
      kind,
      createdAt: Date.now(),
      manifest: document.manifest,
      rawJson,
    };
    const next = ExamStore.addVaultSnapshot(snap);
    setVaultSnapshots(next);
  };

  const handleDeleteSnapshot = (snapshotId: string) => {
    const next = vaultSnapshots.filter((s) => s.id !== snapshotId);
    setVaultSnapshots(next);
    ExamStore.saveVaultSnapshots(next);
    showToast('Deleted local vault snapshot');
  };

  const handleExecuteRestore = (preview: RestorePreview, mode: RestoreMode) => {
    if (mode === 'FULL_REPLACE') {
      const preArchive = buildVaultArchive(workspaceBackupState, 'Pre-Replace Safety Guard');
      handleCreateSnapshot('Auto Guard (Pre-Replace)', 'pre_restore', preArchive.rawJson);
    }

    const merged = applyVaultRestoreToState(
      workspaceBackupState,
      preview,
      mode,
      DEFAULT_ROOT_FOLDER
    );

    const safePapers =
      merged.papers.length > 0 ? merged.papers : [normalizePaperMetadata(DEFAULT_PAPER)];
    setPapers(safePapers);
    ExamStore.savePapers(safePapers);

    const activeMatch =
      safePapers.find((p) => p.metadata.id === currentPaper.metadata.id) || safePapers[0];
    setCurrentPaper(activeMatch);
    ExamStore.setActivePaper(activeMatch);

    setFolders(merged.folders);
    ExamStore.saveFolders(merged.folders);

    setAttempts(merged.attempts);
    localStorage.setItem('examset_attempts_v2', JSON.stringify(merged.attempts));

    setCountdowns(merged.countdowns);
    ExamStore.saveCountdowns(merged.countdowns);

    setStudyNotes(merged.studyNotes);
    ExamStore.saveStudyNotes(merged.studyNotes);

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
        setActiveTab('student_portal');
      }
    } else if (sugg.category === 'FLASHCARDS') {
      setActiveTab('knowledge_studio');
    } else if (sugg.category === 'VOCABULARY') {
      setActiveTab('vocabulary_hub');
    } else if (sugg.category === 'HEADWORD_INBOX') {
      setActiveTab('vocabulary_hub');
      setShowHeadwordInboxModal(true);
    }
  };

  const handleJumpToTodoLink = (todo: StudyTodoItem) => {
    if (todo.linkedType === 'PAPER' && todo.linkedPaperId) {
      const p = papers.find((x) => x.metadata.id === todo.linkedPaperId);
      if (p) {
        setCurrentPaper(p);
        setActiveTab('student_portal');
      }
    } else if (todo.linkedType === 'NOTE') {
      setActiveTab('knowledge_studio');
    } else if (todo.linkedType === 'VOCAB_DECK') {
      setActiveTab('vocabulary_hub');
    }
  };

  const handleGuideNavigate = (dest: GuideDestination) => {
    switch (dest) {
      case 'CREATOR':
        setActiveTab('creator');
        break;
      case 'MASTER_VIEW':
        setActiveTab('master');
        break;
      case 'PRINT_OMR':
        setActiveTab('print');
        break;
      case 'STUDENT_TEST':
        setActiveTab('student_portal');
        break;
      case 'FOLDERS':
        setActiveTab('folders');
        break;
      case 'KNOWLEDGE_STUDIO':
        setActiveTab('knowledge_studio');
        break;
      case 'DICTATION_LAB':
        setActiveTab('dictation');
        break;
      case 'VOCABULARY_HUB':
        setActiveTab('vocabulary_hub');
        break;
      case 'HEADWORD_INBOX':
        setActiveTab('vocabulary_hub');
        setShowHeadwordInboxModal(true);
        break;
      case 'AI_PROMPT_STUDIO':
        setActiveTab('prompt_generator');
        break;
      case 'STUDY_PLANNER':
        setActiveTab('study_planner');
        break;
      case 'BRAIN_LAB':
        setActiveTab('brain_lab');
        break;
      case 'BACKUP_VAULT':
        setActiveTab('backup_vault');
        break;
      case 'COUNTDOWNS':
        setShowCountdownsModal(true);
        break;
    }
  };

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
      { id: 'master', label: 'Master View', shortLabel: 'Master', icon: <Eye className="w-4 h-4 flex-shrink-0" />, badge: `${currentPaper.questions.length}`, group: 'exam' },
      { id: 'print', label: 'Print & OMR Studio', shortLabel: 'Print/OMR', icon: <Printer className="w-4 h-4 flex-shrink-0" />, group: 'exam' },
      { id: 'student_portal', label: 'Student Test Portal', shortLabel: 'Test', icon: <CheckCircle2 className="w-4 h-4 flex-shrink-0" />, group: 'exam' },
      { id: 'folders', label: 'Library & Folders', shortLabel: 'Library', icon: <Folder className="w-4 h-4 flex-shrink-0" />, badge: `${papers.length}`, group: 'exam' },
      { id: 'knowledge_studio', label: 'Knowledge Studio', shortLabel: 'Knowledge', icon: <BookMarked className="w-4 h-4 flex-shrink-0" />, badge: `${studyNotes.length}`, group: 'study' },
      { id: 'dictation', label: 'Dictation Lab', shortLabel: 'Dictation', icon: <Mic className="w-4 h-4 flex-shrink-0" />, group: 'study' },
      { id: 'vocabulary_hub', label: 'Vocabulary Hub', shortLabel: 'Vocab', icon: <Globe className="w-4 h-4 flex-shrink-0" />, badge: `${totalVocabWords}`, group: 'study' },
      { id: 'prompt_generator', label: 'AI Prompt Studio', shortLabel: 'Prompts', icon: <Sparkles className="w-4 h-4 flex-shrink-0" />, group: 'study' },
      { id: 'study_planner', label: 'Study Planner', shortLabel: 'Planner', icon: <CalendarCheck className="w-4 h-4 flex-shrink-0" />, badge: openTodoCount > 0 ? `${openTodoCount}` : undefined, group: 'focus' },
      { id: 'brain_lab', label: 'Brain & Calm Lab', shortLabel: 'Brain Lab', icon: <Brain className="w-4 h-4 flex-shrink-0" />, group: 'focus' },
      { id: 'backup_vault', label: 'Backup Vault', shortLabel: 'Vault', icon: <Archive className="w-4 h-4 flex-shrink-0" />, badge: vaultSnapshots.length > 0 ? `${vaultSnapshots.length}` : undefined, group: 'focus' },
      { id: 'share', label: 'Share & Export', shortLabel: 'Share', icon: <Share2 className="w-4 h-4 flex-shrink-0" />, group: 'focus' },
    ],
    [currentPaper.questions.length, papers.length, studyNotes.length, totalVocabWords, openTodoCount, vaultSnapshots.length]
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
            <div className="flex items-center gap-2.5 sm:gap-3 min-w-0 flex-1">
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
                    Set {currentPaper.metadata.setNumber || '01'} • A4 OMR • SM-2
                  </span>
                </div>
                <p
                  className="es-scroll-x text-[0.6875rem] font-medium"
                  style={{ color: colors.muted }}
                  title={`${currentPaper.metadata.title} (${currentPaper.questions.length} Qs)`}
                >
                  {currentPaper.metadata.title} <span className="tabular-nums opacity-85">({currentPaper.questions.length} Qs)</span>
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
                    onClick={() => setActiveTab(item.id)}
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
                          onClick={() => {
                            setActiveTab(item.id);
                            setMobileMenuOpen(false);
                          }}
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
                        onClick={() => setActiveTab(item.id)}
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
              onNavigateToMaster={() => setActiveTab('master')}
              onNavigateToStudentPortal={() => setActiveTab('student_portal')}
              onNavigateToPrint={() => setActiveTab('print')}
              onOpenCreateModal={() => {
                setCreateDefaultFolderId(currentPaper.metadata.folderId || 'default');
                setCreateInitialText('');
                setShowCreateModal(true);
              }}
              onOpenBatchImportModal={() => setShowBatchImportModal(true)}
              onOpenEditMetadata={() => setEditingMetadataPaper(currentPaper)}
              onOpenAuditModal={() => setShowAuditModal(true)}
            />
          )}

          {activeTab === 'master' && (
            <MasterViewTab
              currentPaper={currentPaper}
              onUpdatePaper={handleSavePaper}
              onOpenEditMetadata={() => setEditingMetadataPaper(currentPaper)}
              onOpenAuditModal={() => setShowAuditModal(true)}
            />
          )}

          {activeTab === 'print' && (
            <PrintGeneratorTab currentPaper={currentPaper} allPapers={papers} />
          )}

          {activeTab === 'student_portal' && (
            <StudentPortalTab
              currentPaper={currentPaper}
              attempts={attempts}
              onSaveAttempt={handleSaveAttempt}
              onDeleteAttempt={handleDeleteAttempt}
            />
          )}

          {activeTab === 'folders' && (
            <FolderManagerTab
              currentPaper={currentPaper}
              papers={papers}
              folders={folders}
              countdowns={countdowns}
              onSelectPaper={handleSelectPaper}
              onNavigateToMaster={() => setActiveTab('master')}
              onNavigateToStudentPortal={() => setActiveTab('student_portal')}
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
            />
          )}

          {activeTab === 'knowledge_studio' && (
            <KnowledgeStudioTab
              notes={studyNotes}
              papers={papers}
              attempts={attempts}
              folders={folders}
              onSaveNote={handleSaveStudyNote}
              onDeleteNote={handleDeleteStudyNote}
              onOpenEditor={(note) => {
                setEditingStudyNote(note || null);
                setStudyNotePrefill(null);
                setShowStudyNoteModal(true);
              }}
              onSendNoteToDictation={(noteId) => {
                setDictationTargetNoteId(noteId);
                setActiveTab('dictation');
              }}
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
              defaultEnglishVariant={englishVariant}
              onNavigateToCreator={() => setActiveTab('creator')}
              onNavigateToKnowledge={() => setActiveTab('knowledge_studio')}
              onSendToCreator={(promptText) => {
                setCreateDefaultFolderId(currentPaper.metadata.folderId || 'default');
                setCreateInitialText(promptText);
                setShowCreateModal(true);
              }}
              onCreateStudyNoteFromPrompt={(collectionType, subject, topic) => {
                setEditingStudyNote(null);
                setStudyNotePrefill({ collectionType, subject, topic });
                setActiveTab('knowledge_studio');
                setShowStudyNoteModal(true);
              }}
              onOpenVocabImport={() => {
                setActiveTab('vocabulary_hub');
                setShowVocabImportModal(true);
              }}
              onOpenHeadwordInbox={() => {
                setActiveTab('vocabulary_hub');
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
              onNotify={showToast}
            />
          )}

          {activeTab === 'share' && (
            <ShareSystemTab
              currentPaper={currentPaper}
              allPapers={papers}
              onImportPaper={(paper) => {
                handleSavePaper(paper);
                setActiveTab('master');
              }}
              onImportPapers={handleBatchImport}
              onOpenBatchImport={() => setShowBatchImportModal(true)}
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
              onClick={() => {
                setActiveTab(item.id);
                setMobileMenuOpen(false);
              }}
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
          defaultFolderId={currentPaper.metadata.folderId || 'default'}
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

      {showAuditModal && (
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
          onSaveCountdowns={handleSaveCountdowns}
          onClose={() => setShowCountdownsModal(false)}
        />
      )}

      {/* Phase 4 Modals */}
      {showStudyNoteModal && (
        <StudyNoteEditorModal
          isOpen={showStudyNoteModal}
          initialNote={editingStudyNote}
          prefill={studyNotePrefill}
          papers={papers}
          folders={folders}
          onSaveNote={handleSaveStudyNote}
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
