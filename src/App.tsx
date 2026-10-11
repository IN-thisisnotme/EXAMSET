import React, { useState, useEffect } from 'react';
import { 
  FileText, 
  Printer, 
  CheckCircle2, 
  Folder, 
  Sparkles, 
  Share2, 
  Eye, 
  BookOpen,
  Menu,
  X,
  Bell
} from 'lucide-react';
import { QuestionPaper, DEFAULT_EXAM_CONFIG } from './types/exam';
import { CreatorTab } from './components/tabs/CreatorTab';
import { PrintGeneratorTab } from './components/tabs/PrintGeneratorTab';
import { StudentPortalTab } from './components/tabs/StudentPortalTab';
import { MasterViewTab } from './components/tabs/MasterViewTab';
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

export interface FolderItem {
  id: string;
  name: string;
  color: string;
  createdAt: number;
}

export const DEFAULT_PAPER: QuestionPaper = {
  metadata: {
    id: 'paper-default-01',
    title: 'National Science & Technology Olympiad',
    subject: 'General Science',
    topic: 'Physics & Chemistry Core',
    setNumber: 'Set A',
    targetExam: 'All India Competitive Exam',
    createdAt: Date.now(),
    folderId: 'default'
  },
  questions: [
    {
      id: 'q1',
      num: 1,
      question: 'Which chemical compound is commonly known as dry ice and sublimes directly into gas at -78.5°C?',
      options: {
        a: 'Solid Carbon Dioxide (CO₂)',
        b: 'Solid Nitrogen (N₂)',
        c: 'Solid Carbon Monoxide (CO)',
        d: 'Solid Methane (CH₄)'
      },
      correctAnswer: 'A',
      explanation: 'Dry ice is the solid form of carbon dioxide (CO₂). Under atmospheric pressure, it sublimes directly into vapor without entering a liquid phase.'
    },
    {
      id: 'q2',
      num: 2,
      question: 'What is the approximate velocity of light in a complete vacuum?',
      options: {
        a: '3 × 10⁶ m/s',
        b: '3 × 10⁸ m/s',
        c: '3 × 10¹⁰ m/s',
        d: '1.5 × 10⁸ m/s'
      },
      correctAnswer: 'B',
      explanation: 'The speed of light in vacuum is defined as exactly 299,792,458 m/s, approximately 3 × 10⁸ m/s.'
    },
    {
      id: 'q3',
      num: 3,
      question: 'Which organelle in eukaryotic cells is primarily responsible for generating adenosine triphosphate (ATP)?',
      options: {
        a: 'Endoplasmic Reticulum',
        b: 'Golgi Apparatus',
        c: 'Mitochondria',
        d: 'Lysosome'
      },
      correctAnswer: 'C',
      explanation: 'Mitochondria generate cellular ATP via oxidative phosphorylation.'
    },
    {
      id: 'q4',
      num: 4,
      question: 'What is the pH value of pure neutral water at standard room temperature (25°C)?',
      options: {
        a: '5.5',
        b: '7.0',
        c: '8.5',
        d: '0.0'
      },
      correctAnswer: 'B',
      explanation: 'Pure water at 25°C has equal concentrations of H+ and OH- ions, resulting in a neutral pH of 7.0.'
    },
    {
      id: 'q5',
      num: 5,
      question: 'Which allotrope of carbon consists of a single planar sheet of sp²-hybridized carbon atoms arranged in a honeycomb lattice?',
      options: {
        a: 'Diamond',
        b: 'Fullerenes',
        c: 'Graphene',
        d: 'Carbon Nanotubes'
      },
      correctAnswer: 'C',
      explanation: 'Graphene is a single atom-thick two-dimensional honeycomb sheet of carbon atoms.'
    }
  ]
};

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ActiveTab>('creator');
  const [currentPaper, setCurrentPaper] = useState<QuestionPaper>(() => {
    const saved = localStorage.getItem('examset_active_paper');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        console.error('Failed to parse cached paper', e);
      }
    }
    return DEFAULT_PAPER;
  });

  const [notification, setNotification] = useState<string | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);

  useEffect(() => {
    localStorage.setItem('examset_active_paper', JSON.stringify(currentPaper));
  }, [currentPaper]);

  const showToast = (message: string) => {
    setNotification(message);
    setTimeout(() => {
      setNotification(null);
    }, 3200);
  };

  const handleSavePaper = (paper: QuestionPaper, silent: boolean = false) => {
    setCurrentPaper(paper);
    if (!silent) {
      showToast(`Saved "${paper.metadata.title}" (${paper.questions.length} questions)`);
    }
  };

  const navItems: { id: ActiveTab; label: string; icon: React.ReactNode; badge?: string }[] = [
    { id: 'creator', label: 'Question Creator', icon: <FileText className="w-4 h-4" /> },
    { id: 'master', label: 'Master View', icon: <Eye className="w-4 h-4" />, badge: `${currentPaper.questions.length}` },
    { id: 'print', label: 'Print & OMR Generator', icon: <Printer className="w-4 h-4" /> },
    { id: 'student_portal', label: 'Student Test Portal', icon: <CheckCircle2 className="w-4 h-4" /> },
    { id: 'folders', label: 'Folders & Library', icon: <Folder className="w-4 h-4" /> },
    { id: 'prompt_generator', label: 'AI Prompt Builder', icon: <Sparkles className="w-4 h-4" /> },
    { id: 'share', label: 'Share & Export', icon: <Share2 className="w-4 h-4" /> }
  ];

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans">
      {/* Toast Notification */}
      {notification && (
        <div className="fixed bottom-5 right-5 z-50 flex items-center gap-2 bg-slate-900 text-white px-4 py-2.5 rounded-lg shadow-xl border border-slate-700">
          <Bell className="w-4 h-4 text-emerald-400" />
          <span className="text-sm font-medium">{notification}</span>
        </div>
      )}

      {/* Main Top Header */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-blue-600 flex items-center justify-center text-white font-black shadow-sm">
                <BookOpen className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-lg font-black tracking-tight text-slate-900">ExamSet</h1>
                  <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-700 font-semibold border border-blue-200">
                    A4 OMR Ready
                  </span>
                </div>
                <p className="text-xs text-slate-500 font-medium truncate max-w-xs sm:max-w-md">
                  {currentPaper.metadata.title} • {currentPaper.metadata.setNumber} ({currentPaper.questions.length} Questions)
                </p>
              </div>
            </div>

            {/* Desktop Navigation Tabs */}
            <nav className="hidden lg:flex items-center space-x-1">
              {navItems.map((item) => {
                const isActive = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-md text-xs font-semibold transition-colors duration-150 ${
                      isActive 
                        ? 'bg-blue-600 text-white shadow-sm' 
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                    }`}
                  >
                    {item.icon}
                    <span>{item.label}</span>
                    {item.badge && (
                      <span className={`ml-1 text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                        isActive ? 'bg-blue-700 text-white' : 'bg-slate-200 text-slate-700'
                      }`}>
                        {item.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </nav>

            {/* Mobile Menu Button */}
            <div className="flex lg:hidden items-center">
              <button
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="p-2 rounded-md text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                aria-label="Toggle Navigation Menu"
              >
                {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
              </button>
            </div>
          </div>
        </div>

        {/* Mobile Dropdown Menu */}
        {mobileMenuOpen && (
          <div className="lg:hidden border-t border-slate-200 bg-white px-3 py-3 space-y-1 shadow-lg">
            {navItems.map((item) => {
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => {
                    setActiveTab(item.id);
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-md text-sm font-semibold transition-colors ${
                    isActive 
                      ? 'bg-blue-600 text-white' 
                      : 'text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    {item.icon}
                    <span>{item.label}</span>
                  </div>
                  {item.badge && (
                    <span className={`text-xs px-2 py-0.5 rounded-full font-bold ${
                      isActive ? 'bg-blue-700 text-white' : 'bg-slate-100 text-slate-700'
                    }`}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </header>

      {/* Primary Tab Viewport Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6 lg:p-8">
        <section className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 min-h-[600px]">
          {activeTab === 'creator' && (
            <CreatorTab
              currentPaper={currentPaper}
              onSavePaper={handleSavePaper}
              onNavigateToMaster={() => setActiveTab('master')}
            />
          )}

          {activeTab === 'master' && (
            <MasterViewTab
              currentPaper={currentPaper}
              onUpdatePaper={handleSavePaper}
            />
          )}

          {activeTab === 'print' && (
            <PrintGeneratorTab currentPaper={currentPaper} />
          )}

          {activeTab === 'student_portal' && (
            <StudentPortalTab currentPaper={currentPaper} />
          )}

          {activeTab === 'folders' && (
            <FolderManagerTab
              currentPaper={currentPaper}
              onSelectPaper={(paper) => setCurrentPaper(paper)}
              onNavigateToMaster={() => setActiveTab('master')}
            />
          )}

          {activeTab === 'prompt_generator' && (
            <PromptGeneratorTab
              onNavigateToCreator={() => setActiveTab('creator')}
            />
          )}

          {activeTab === 'share' && (
            <ShareSystemTab
              currentPaper={currentPaper}
              onImportPaper={(paper) => {
                setCurrentPaper(paper);
                setActiveTab('master');
              }}
            />
          )}
        </section>
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-slate-200 py-3 text-center text-xs text-slate-500">
        ExamSet Engine • Strict Single-Sheet A4 OMR & Question Paper System
      </footer>
    </div>
  );
};

export default App;
