import React, { useEffect, useMemo, useState } from 'react';
import {
  Bell,
  BookOpen,
  CheckCircle2,
  Eye,
  FileText,
  Folder,
  Menu,
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
import { ThemeProvider, useTheme } from './theme/ThemeProvider';
import { ExamStore, normalizePaperMetadata } from './data/examStore';
import { ThemePaletteModal } from './components/ThemePaletteModal';
import { FontScaleModal } from './components/FontScaleModal';
import { InputModal } from './components/InputModal';
import { BatchImportModal } from './components/BatchImportModal';
import { EditMetadataModal } from './components/EditMetadataModal';
import { AuditResolutionModal } from './components/AuditResolutionModal';
import { ExamCountdownsModal } from './components/ExamCountdownsModal';
import { CreatorTab } from './components/tabs/CreatorTab';
import { MasterViewTab } from './components/tabs/MasterViewTab';
import { PrintGeneratorTab } from './components/tabs/PrintGeneratorTab';
import { StudentPortalTab } from './components/tabs/StudentPortalTab';
import { FolderManagerTab } from './components/tabs/FolderManagerTab';
import { PromptGeneratorTab } from './components/tabs/PromptGeneratorTab';
import { ShareSystemTab } from './components/tabs/ShareSystemTab';

export type ActiveTab =
  | 'creator'
  | 'master'
  | 'print'
  | 'student_portal'
  | 'folders'
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
    // Move papers cleanly back to root folder before removing custom folder
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

  const navItems: { id: ActiveTab; label: string; icon: React.ReactNode; badge?: string }[] = useMemo(
    () => [
      { id: 'creator', label: 'Question Creator', icon: <FileText className="w-4 h-4" /> },
      { id: 'master', label: 'Master View', icon: <Eye className="w-4 h-4" />, badge: `${currentPaper.questions.length}` },
      { id: 'print', label: 'Print & OMR', icon: <Printer className="w-4 h-4" /> },
      { id: 'student_portal', label: 'Student Portal', icon: <CheckCircle2 className="w-4 h-4" /> },
      { id: 'folders', label: 'Folders', icon: <Folder className="w-4 h-4" />, badge: `${papers.length}` },
      { id: 'prompt_generator', label: 'Prompt Studio', icon: <Sparkles className="w-4 h-4" /> },
      { id: 'share', label: 'Share & Export', icon: <Share2 className="w-4 h-4" /> },
    ],
    [currentPaper.questions.length, papers.length]
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
          <div className="flex items-center justify-between h-16 gap-3">
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
                    Set {currentPaper.metadata.setNumber || '01'} • A4 OMR
                  </span>
                </div>
                <p className="text-[11px] font-medium truncate max-w-[180px] sm:max-w-xs" style={{ color: colors.muted }}>
                  {currentPaper.metadata.title} ({currentPaper.questions.length} Qs)
                </p>
              </div>
            </div>

            {/* Desktop Navigation */}
            <nav className="hidden xl:flex items-center gap-1">
              {navItems.map((item) => {
                const isActive = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className="flex items-center gap-1.5 px-2.5 py-2 rounded-lg text-xs font-bold transition"
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
                );
              })}
            </nav>

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
                className="xl:hidden p-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                aria-label="Toggle Navigation"
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
            </div>
          </div>

          {/* Medium Tablet Compact Tab Strip */}
          <div className="hidden md:flex xl:hidden items-center gap-1 pb-2 overflow-x-auto">
            {navItems.map((item) => {
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition"
                  style={
                    isActive
                      ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                      : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                  }
                >
                  {item.icon}
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Mobile Drawer Menu */}
        {mobileMenuOpen && (
          <div
            className="xl:hidden border-t px-3 py-3 space-y-1 shadow-lg"
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

          {activeTab === 'prompt_generator' && (
            <PromptGeneratorTab
              onNavigateToCreator={() => setActiveTab('creator')}
              onSendToCreator={(promptText) => {
                setCreateDefaultFolderId(currentPaper.metadata.folderId || 'default');
                setCreateInitialText(promptText);
                setShowCreateModal(true);
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
        ExamSet Suite • Single-Page A4 OMR • CBT &amp; CTT Psychometric Engine
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
    </div>
  );
};

export const App: React.FC = () => (
  <ThemeProvider>
    <ExamSetWorkspace />
  </ThemeProvider>
);

export default App;
