import React, { useEffect, useMemo, useState } from 'react';
import {
  Bell,
  BookMarked,
  BookOpen,
  CheckCircle2,
  Eye,
  FileText,
  Folder,
  Globe,
  Menu,
  Mic,
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
import { ThemeProvider, useTheme } from './theme/ThemeProvider';
import { ExamStore, normalizePaperMetadata } from './data/examStore';
import { STARTER_STUDY_NOTES, STARTER_VOCAB_DECKS } from './data/phase4Seeds';
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
  const { colors, isDark } = useTheme();
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

  const totalVocabWords = useMemo(
    () => vocabDecks.reduce((sum, d) => sum + d.entries.length, 0),
    [vocabDecks]
  );

  const navItems: { id: ActiveTab; label: string; icon: React.ReactNode; badge?: string; group: 'exam' | 'study' }[] = useMemo(
    () => [
      { id: 'creator', label: 'Question Creator', icon: <FileText className="w-4 h-4" />, group: 'exam' },
      { id: 'master', label: 'Master View', icon: <Eye className="w-4 h-4" />, badge: `${currentPaper.questions.length}`, group: 'exam' },
      { id: 'print', label: 'Print & OMR', icon: <Printer className="w-4 h-4" />, group: 'exam' },
      { id: 'student_portal', label: 'Student Portal', icon: <CheckCircle2 className="w-4 h-4" />, group: 'exam' },
      { id: 'folders', label: 'Folders', icon: <Folder className="w-4 h-4" />, badge: `${papers.length}`, group: 'exam' },
      { id: 'knowledge_studio', label: 'Knowledge Studio', icon: <BookMarked className="w-4 h-4" />, badge: `${studyNotes.length}`, group: 'study' },
      { id: 'dictation', label: 'Dictation Lab', icon: <Mic className="w-4 h-4" />, group: 'study' },
      { id: 'vocabulary_hub', label: 'Vocabulary Hub', icon: <Globe className="w-4 h-4" />, badge: `${totalVocabWords}`, group: 'study' },
      { id: 'prompt_generator', label: 'Prompt Studio', icon: <Sparkles className="w-4 h-4" />, group: 'study' },
      { id: 'share', label: 'Share & Export', icon: <Share2 className="w-4 h-4" />, group: 'study' },
    ],
    [currentPaper.questions.length, papers.length, studyNotes.length, totalVocabWords]
  );

  return (
    <div
      className="min-h-screen flex flex-col font-sans transition-colors duration-200"
      style={{ backgroundColor: colors.bg, color: colors.ink }}
    >
      {/* Toast Notification */}
      {notification && (
        <div
          className="fixed bottom-5 right-5 z-50 flex items-center gap-2 px-4 py-2.5 rounded-xl shadow-2xl border"
          style={{
            backgroundColor: colors.navyBanner,
            color: colors.navyBannerText,
            borderColor: colors.teal,
          }}
        >
          <Bell className="w-4 h-4 text-emerald-300" />
          <span className="text-sm font-semibold">{notification}</span>
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
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-14 gap-3">
            {/* Brand + Active Set Summary */}
            <div className="flex items-center gap-3 min-w-0">
              <div
                className="w-9 h-9 rounded-xl flex items-center justify-center font-black shadow-sm flex-shrink-0"
                style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
              >
                <BookOpen className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h1 className="text-base sm:text-lg font-black tracking-tight" style={{ color: colors.inkBold }}>
                    ExamSet
                  </h1>
                  <span
                    className="hidden sm:inline-block text-[10px] px-2 py-0.5 rounded-full font-bold border"
                    style={{
                      backgroundColor: colors.tealLight,
                      color: colors.tealDark,
                      borderColor: colors.border,
                    }}
                  >
                    Set {currentPaper.metadata.setNumber || '01'} • A4 OMR • SM-2
                  </span>
                </div>
                <p className="text-[11px] font-medium truncate max-w-[180px] sm:max-w-sm" style={{ color: colors.muted }}>
                  {currentPaper.metadata.title} ({currentPaper.questions.length} Qs)
                </p>
              </div>
            </div>

            {/* Theme / Typography Controls + Mobile Toggle */}
            <div className="flex items-center gap-1.5 flex-shrink-0">
              <button
                onClick={() => setShowThemeModal(true)}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-bold transition"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                title="Color Scheme & Light/Dark Mode"
              >
                <Palette className="w-4 h-4" style={{ color: colors.teal }} />
                <span className="hidden md:inline">{isDark ? 'Dark' : 'Light'}</span>
              </button>

              <button
                onClick={() => setShowFontModal(true)}
                className="p-2 rounded-lg border transition"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                title="Typography Scale"
              >
                <Type className="w-4 h-4" style={{ color: colors.teal }} />
              </button>

              <button
                onClick={() => setMobileMenuOpen((o) => !o)}
                className="md:hidden p-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                aria-label="Toggle Navigation"
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
            </div>
          </div>

          {/* Desktop & Tablet Clean Scrollable Navigation Strip */}
          <nav
            className="hidden md:flex items-center gap-1 pb-2 pt-1 overflow-x-auto border-t"
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
                    onClick={() => setActiveTab(item.id)}
                    className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition flex-shrink-0"
                    style={
                      isActive
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                        : { color: colors.inkMuted, backgroundColor: 'transparent' }
                    }
                  >
                    {item.icon}
                    <span>{item.label}</span>
                    {item.badge && (
                      <span
                        className="ml-0.5 text-[10px] px-1.5 py-0.2 rounded-full font-bold"
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

        {/* Mobile Drawer Menu */}
        {mobileMenuOpen && (
          <div
            className="md:hidden border-t px-3 py-3 space-y-1 shadow-lg max-h-[75vh] overflow-y-auto"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            {navItems.map((item) => {
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => {
                    setActiveTab(item.id);
                    setMobileMenuOpen(false);
                  }}
                  className="w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-bold"
                  style={
                    isActive
                      ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                      : { color: colors.ink, backgroundColor: colors.surfaceVariant }
                  }
                >
                  <div className="flex items-center gap-2.5">
                    {item.icon}
                    <span>{item.label}</span>
                  </div>
                  {item.badge && (
                    <span className="text-xs px-2 py-0.5 rounded-full bg-black/15">{item.badge}</span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        <section
          className="rounded-2xl shadow-sm border p-4 sm:p-6 min-h-[620px]"
          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
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

      {/* Footer */}
      <footer
        className="border-t py-3 text-center text-xs"
        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
      >
        ExamSet Suite • Single-Page A4 OMR • Knowledge Studio &amp; SM-2 • Dictation Lab • Vocabulary Hub
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
    </div>
  );
};

export const App: React.FC = () => (
  <ThemeProvider>
    <ExamSetWorkspace />
  </ThemeProvider>
);

export default App;
