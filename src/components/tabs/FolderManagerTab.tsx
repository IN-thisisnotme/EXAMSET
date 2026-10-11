import React, { useState, useEffect } from 'react';
import { 
  Folder, 
  FolderPlus, 
  FileText, 
  Search, 
  Trash2, 
  Edit2, 
  MoveRight, 
  Check, 
  Plus, 
  Clock, 
  Layers,
  ArrowRight,
  FolderOpen
} from 'lucide-react';
import { QuestionPaper } from '../../types/exam';

export interface FolderItem {
  id: string;
  name: string;
  color: string;
  createdAt: number;
}

interface FolderManagerTabProps {
  currentPaper: QuestionPaper;
  onSelectPaper: (paper: QuestionPaper) => void;
  onNavigateToMaster: () => void;
}

const DEFAULT_FOLDERS: FolderItem[] = [
  { id: 'f-science', name: 'Science & Emerging Tech', color: '#0284c7', createdAt: Date.now() - 86400000 * 2 },
  { id: 'f-upsc', name: 'UPSC Civil Services / PSC', color: '#16a34a', createdAt: Date.now() - 86400000 * 5 },
  { id: 'f-ssc', name: 'SSC CGL / Banking', color: '#d97706', createdAt: Date.now() - 86400000 * 10 }
];

export const FolderManagerTab: React.FC<FolderManagerTabProps> = ({
  currentPaper,
  onSelectPaper,
  onNavigateToMaster
}) => {
  const [folders, setFolders] = useState<FolderItem[]>(() => {
    const saved = localStorage.getItem('examset_saved_folders');
    return saved ? JSON.parse(saved) : DEFAULT_FOLDERS;
  });

  const [savedPapers, setSavedPapers] = useState<QuestionPaper[]>(() => {
    const saved = localStorage.getItem('examset_saved_library');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        console.error(e);
      }
    }
    return [
      currentPaper,
      {
        ...currentPaper,
        metadata: {
          ...currentPaper.metadata,
          id: 'paper-history-02',
          title: 'Indian Polity & Constitutional Framework',
          subject: 'Indian Polity',
          setNumber: 'Set B',
          createdAt: Date.now() - 86400000 * 3,
          folderId: 'f-upsc'
        }
      }
    ];
  });

  const [activeFolderId, setActiveFolderId] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [showNewFolderModal, setShowNewFolderModal] = useState<boolean>(false);
  const [newFolderName, setNewFolderName] = useState<string>('');
  const [newFolderColor, setNewFolderColor] = useState<string>('#0284c7');

  // Persistence
  useEffect(() => {
    localStorage.setItem('examset_saved_folders', JSON.stringify(folders));
  }, [folders]);

  useEffect(() => {
    localStorage.setItem('examset_saved_library', JSON.stringify(savedPapers));
  }, [savedPapers]);

  const handleCreateFolder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;

    const newFolder: FolderItem = {
      id: `f-${Date.now().toString(36)}`,
      name: newFolderName.trim(),
      color: newFolderColor,
      createdAt: Date.now()
    };

    setFolders([...folders, newFolder]);
    setNewFolderName('');
    setShowNewFolderModal(false);
  };

  const handleDeleteFolder = (folderId: string) => {
    if (window.confirm('Delete this folder? Papers inside will be moved to Uncategorized.')) {
      setFolders(folders.filter(f => f.id !== folderId));
      setSavedPapers(savedPapers.map(p => p.metadata.folderId === folderId ? { ...p, metadata: { ...p.metadata, folderId: undefined } } : p));
      if (activeFolderId === folderId) {
        setActiveFolderId('all');
      }
    }
  };

  const handleMovePaper = (paperId: string, targetFolderId: string) => {
    const updated = savedPapers.map(p => {
      if (p.metadata.id === paperId) {
        return {
          ...p,
          metadata: {
            ...p.metadata,
            folderId: targetFolderId === 'none' ? undefined : targetFolderId
          }
        };
      }
      return p;
    });
    setSavedPapers(updated);
  };

  const handleDeletePaper = (paperId: string) => {
    if (window.confirm('Delete this question paper from your library?')) {
      setSavedPapers(savedPapers.filter(p => p.metadata.id !== paperId));
    }
  };

  const handleSaveCurrentPaperToLibrary = () => {
    const exists = savedPapers.some(p => p.metadata.id === currentPaper.metadata.id);
    if (exists) {
      setSavedPapers(savedPapers.map(p => p.metadata.id === currentPaper.metadata.id ? currentPaper : p));
    } else {
      setSavedPapers([currentPaper, ...savedPapers]);
    }
    alert(`"${currentPaper.metadata.title}" saved to library!`);
  };

  // Filter papers
  const filteredPapers = savedPapers.filter(p => {
    const matchesFolder = activeFolderId === 'all' || 
      (activeFolderId === 'uncategorized' ? !p.metadata.folderId : p.metadata.folderId === activeFolderId);

    const matchesSearch = searchQuery.trim() === '' ||
      p.metadata.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.metadata.subject.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.metadata.setNumber.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesFolder && matchesSearch;
  });

  return (
    <div className="space-y-6">
      {/* Top Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <FolderOpen className="w-5 h-5 text-blue-600" />
            Library & Assessment Folder Manager
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Organize question sets into subject folders, manage past papers, and easily load tests into your active workspace.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleSaveCurrentPaperToLibrary}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white text-slate-700 border border-slate-300 rounded-lg hover:bg-slate-50 transition shadow-sm"
          >
            <Layers className="w-3.5 h-3.5 text-slate-500" />
            Save Current Paper
          </button>

          <button
            onClick={() => setShowNewFolderModal(true)}
            className="flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
          >
            <FolderPlus className="w-4 h-4" />
            New Folder
          </button>
        </div>
      </div>

      {/* Main Content Layout: Sidebar Folders + Papers Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Folders List */}
        <div className="lg:col-span-4 space-y-3">
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm space-y-3">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Folders ({folders.length})
              </h4>
            </div>

            <div className="space-y-1">
              <button
                onClick={() => setActiveFolderId('all')}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-semibold transition ${
                  activeFolderId === 'all'
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                <div className="flex items-center gap-2">
                  <Folder className="w-4 h-4" />
                  <span>All Question Papers</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                  activeFolderId === 'all' ? 'bg-blue-700 text-white' : 'bg-slate-200 text-slate-700'
                }`}>
                  {savedPapers.length}
                </span>
              </button>

              {folders.map(folder => {
                const count = savedPapers.filter(p => p.metadata.folderId === folder.id).length;
                const isActive = activeFolderId === folder.id;

                return (
                  <div
                    key={folder.id}
                    className={`group flex items-center justify-between px-3 py-2 rounded-lg text-xs font-semibold transition cursor-pointer ${
                      isActive ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-700 hover:bg-slate-100'
                    }`}
                    onClick={() => setActiveFolderId(folder.id)}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span 
                        className="w-3 h-3 rounded-full flex-shrink-0" 
                        style={{ backgroundColor: folder.color }}
                      />
                      <span className="truncate">{folder.name}</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                        isActive ? 'bg-slate-700 text-white' : 'bg-slate-200 text-slate-700'
                      }`}>
                        {count}
                      </span>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteFolder(folder.id);
                        }}
                        className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 rounded transition"
                        title="Delete Folder"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}

              <button
                onClick={() => setActiveFolderId('uncategorized')}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-semibold transition ${
                  activeFolderId === 'uncategorized'
                    ? 'bg-slate-800 text-white shadow-sm'
                    : 'text-slate-500 hover:bg-slate-100'
                }`}
              >
                <div className="flex items-center gap-2">
                  <Folder className="w-4 h-4 text-slate-400" />
                  <span>Uncategorized</span>
                </div>
                <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-slate-200 text-slate-700">
                  {savedPapers.filter(p => !p.metadata.folderId).length}
                </span>
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: Question Papers in Selected Folder */}
        <div className="lg:col-span-8 space-y-4">
          {/* Search Box */}
          <div className="bg-white p-3 rounded-xl border border-slate-200 flex items-center justify-between gap-3 shadow-sm">
            <div className="relative w-full">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search papers in library by title or subject..."
                className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Papers Grid */}
          <div className="space-y-3">
            {filteredPapers.length === 0 ? (
              <div className="bg-white rounded-xl border border-slate-200 p-12 text-center text-slate-400 text-xs">
                No question papers found in this folder.
              </div>
            ) : (
              filteredPapers.map(paper => {
                const isCurrent = paper.metadata.id === currentPaper.metadata.id;
                const folderObj = folders.find(f => f.id === paper.metadata.folderId);

                return (
                  <div
                    key={paper.metadata.id}
                    className={`bg-white rounded-xl border p-4 shadow-sm transition hover:shadow-md flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                      isCurrent ? 'border-blue-400 bg-blue-50/20' : 'border-slate-200'
                    }`}
                  >
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
                          {paper.metadata.setNumber || 'Set A'}
                        </span>
                        {folderObj && (
                          <span 
                            className="text-[10px] font-semibold text-white px-2 py-0.5 rounded-full"
                            style={{ backgroundColor: folderObj.color }}
                          >
                            {folderObj.name}
                          </span>
                        )}
                        {isCurrent && (
                          <span className="text-[10px] font-bold text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded">
                            Active in Workspace
                          </span>
                        )}
                      </div>

                      <h4 className="text-sm font-bold text-slate-900 leading-snug">
                        {paper.metadata.title}
                      </h4>

                      <div className="flex items-center gap-3 text-xs text-slate-500">
                        <span>{paper.metadata.subject}</span>
                        <span>•</span>
                        <span>{paper.questions.length} Questions</span>
                        <span>•</span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-400" />
                          {new Date(paper.metadata.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
                      {/* Move to Folder Dropdown */}
                      <select
                        value={paper.metadata.folderId || 'none'}
                        onChange={(e) => handleMovePaper(paper.metadata.id, e.target.value)}
                        className="text-xs bg-slate-50 border border-slate-300 text-slate-700 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        title="Move to Folder"
                      >
                        <option value="none">No Folder</option>
                        {folders.map(f => (
                          <option key={f.id} value={f.id}>{f.name}</option>
                        ))}
                      </select>

                      <button
                        onClick={() => {
                          onSelectPaper(paper);
                          onNavigateToMaster();
                        }}
                        className="flex items-center gap-1 px-3 py-1.5 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
                      >
                        <span>Open</span>
                        <ArrowRight className="w-3 h-3" />
                      </button>

                      <button
                        onClick={() => handleDeletePaper(paper.metadata.id)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded hover:bg-slate-100 transition"
                        title="Delete Paper"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* New Folder Modal */}
      {showNewFolderModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-2xl border border-slate-200 space-y-4">
            <h3 className="text-base font-bold text-slate-900">Create New Subject Folder</h3>

            <form onSubmit={handleCreateFolder} className="space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Folder Name</label>
                <input
                  type="text"
                  required
                  value={newFolderName}
                  onChange={(e) => setNewFolderName(e.target.value)}
                  placeholder="e.g. Modern Indian History"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Folder Color Tag</label>
                <div className="flex items-center gap-2">
                  {['#0284c7', '#16a34a', '#d97706', '#dc2626', '#9333ea', '#475569'].map(color => (
                    <button
                      key={color}
                      type="button"
                      onClick={() => setNewFolderColor(color)}
                      className={`w-7 h-7 rounded-full transition flex items-center justify-center ${
                        newFolderColor === color ? 'ring-2 ring-offset-2 ring-slate-800' : ''
                      }`}
                      style={{ backgroundColor: color }}
                    >
                      {newFolderColor === color && <Check className="w-3.5 h-3.5 text-white" />}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowNewFolderModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow"
                >
                  Create
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default FolderManagerTab;
