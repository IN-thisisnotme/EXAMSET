import React, { useMemo, useState } from 'react';
import {
  CalendarClock,
  CheckCircle2,
  CheckSquare,
  ChevronRight,
  Download,
  FilePlus2,
  Folder,
  FolderOpen,
  FolderPlus,
  Layers,
  Play,
  Search,
  Sliders,
  Square,
  Trash2,
  X,
} from 'lucide-react';
import {
  ExamCountdownTarget,
  FolderItem,
  PaperCategory,
  QuestionPaper,
} from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { ExamNumberGenerator } from '../../utils/examNumberGenerator';
import { PaperLanguageDetector } from '../../utils/paperLanguageDetector';
import { getDaysRemaining } from '../ExamCountdownsModal';

interface FolderManagerTabProps {
  currentPaper: QuestionPaper;
  papers: QuestionPaper[];
  folders: FolderItem[];
  countdowns: ExamCountdownTarget[];
  onSelectPaper: (paper: QuestionPaper) => void;
  onNavigateToMaster: () => void;
  onNavigateToStudentPortal: () => void;
  onOpenCreateModal: (folderId?: string) => void;
  onOpenBatchImportModal: (folderId?: string) => void;
  onOpenEditMetadata: (paper: QuestionPaper) => void;
  onOpenCountdownsModal: () => void;
  onCreateFolder: (name: string, color: string, parentId: string | null) => void;
  onDeleteFolder: (folderId: string) => void;
  onMovePapersToFolder: (paperIds: string[], targetFolderId: string) => void;
  onDeletePapers: (paperIds: string[]) => void;
  onTogglePaperCompleted: (paperId: string) => void;
}

const FOLDER_COLORS = ['#177E89', '#17324D', '#247A55', '#D97706', '#4F46E5', '#DC2626'];

export const FolderManagerTab: React.FC<FolderManagerTabProps> = ({
  currentPaper,
  papers,
  folders,
  countdowns,
  onSelectPaper,
  onNavigateToMaster,
  onNavigateToStudentPortal,
  onOpenCreateModal,
  onOpenBatchImportModal,
  onOpenEditMetadata,
  onOpenCountdownsModal,
  onCreateFolder,
  onDeleteFolder,
  onMovePapersToFolder,
  onDeletePapers,
  onTogglePaperCompleted,
}) => {
  const { colors } = useTheme();
  const [activeFolderId, setActiveFolderId] = useState<string>('default');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [stageFilter, setStageFilter] = useState<'ALL' | 'PENDING' | 'COMPLETED'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<PaperCategory | 'ALL'>('ALL');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [isCreatingFolder, setIsCreatingFolder] = useState<boolean>(false);
  const [newFolderName, setNewFolderName] = useState<string>('');
  const [newFolderColor, setNewFolderColor] = useState<string>(FOLDER_COLORS[0]);
  const [moveTargetFolderId, setMoveTargetFolderId] = useState<string>('default');

  const folderById = useMemo(() => new Map(folders.map((f) => [f.id, f])), [folders]);

  // Breadcrumb path for active folder
  const breadcrumbs = useMemo(() => {
    const chain: FolderItem[] = [];
    let cur = folderById.get(activeFolderId);
    const seen = new Set<string>();
    while (cur && !seen.has(cur.id)) {
      chain.unshift(cur);
      seen.add(cur.id);
      cur = cur.parentId ? folderById.get(cur.parentId) : undefined;
    }
    if (chain.length === 0 && folders[0]) chain.push(folders[0]);
    return chain;
  }, [activeFolderId, folderById, folders]);

  // Direct child folders under activeFolderId
  const childFolders = useMemo(() => {
    if (activeFolderId === 'default') {
      return folders.filter((f) => f.id !== 'default' && (!f.parentId || f.parentId === 'default'));
    }
    return folders.filter((f) => f.parentId === activeFolderId);
  }, [folders, activeFolderId]);

  const getDescendantFolderIds = (rootId: string): Set<string> => {
    const set = new Set<string>([rootId]);
    let added = true;
    while (added) {
      added = false;
      for (const f of folders) {
        if (f.parentId && set.has(f.parentId) && !set.has(f.id)) {
          set.add(f.id);
          added = true;
        }
      }
    }
    return set;
  };

  const countPapersRecursive = (folderId: string): number => {
    const ids = getDescendantFolderIds(folderId);
    return papers.filter((p) => ids.has(p.metadata.folderId || 'default')).length;
  };

  const filteredPapers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return papers.filter((p) => {
      const fId = p.metadata.folderId || 'default';
      // When searching globally, search across all folders; otherwise scope to active folder unless in default root
      if (!q && activeFolderId !== 'default' && fId !== activeFolderId) return false;
      if (!q && activeFolderId === 'default' && fId !== 'default') {
        // Show root papers cleanly when clicking root folder; if child folders exist, users click into them
        return false;
      }

      if (stageFilter === 'COMPLETED' && !p.metadata.isCompleted) return false;
      if (stageFilter === 'PENDING' && p.metadata.isCompleted) return false;

      if (categoryFilter !== 'ALL' && p.metadata.manualCategory !== categoryFilter) return false;

      if (q) {
        const metaMatch =
          p.metadata.title.toLowerCase().includes(q) ||
          p.metadata.subject.toLowerCase().includes(q) ||
          p.metadata.targetExam.toLowerCase().includes(q) ||
          (p.metadata.rollNumber || '').includes(q);
        const contentMatch = p.questions.some(
          (item) =>
            item.question.toLowerCase().includes(q) ||
            item.options.a.toLowerCase().includes(q) ||
            item.options.b.toLowerCase().includes(q)
        );
        return metaMatch || contentMatch;
      }
      return true;
    });
  }, [papers, activeFolderId, searchQuery, stageFilter, categoryFilter]);

  const pinnedCountdowns = useMemo(() => countdowns.filter((c) => c.isPinned), [countdowns]);

  const handleToggleSelect = (id: string) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  const handleCreateSubfolder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;
    onCreateFolder(newFolderName.trim(), newFolderColor, activeFolderId);
    setNewFolderName('');
    setIsCreatingFolder(false);
  };

  const handleExportSelectedBundle = () => {
    const chosen = papers.filter((p) => selectedIds.includes(p.metadata.id));
    const list = chosen.length > 0 ? chosen : filteredPapers;
    const blob = new Blob([JSON.stringify({ exportedAt: Date.now(), questionSets: list }, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ExamSet_Bundle_${list.length}_Sets.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExportCohortCsv = () => {
    const chosen = papers.filter((p) => selectedIds.includes(p.metadata.id));
    const list = chosen.length > 0 ? chosen : papers;
    const csv = ExamNumberGenerator.generateCohortManifestCsv(list);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ExamSet_Cohort_Manifest.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      {/* Pinned Countdowns Strip */}
      <div
        className="p-3.5 rounded-xl flex flex-wrap items-center justify-between gap-3"
        style={{ backgroundColor: colors.blueLight, border: `1px solid ${colors.border}` }}
      >
        <div className="flex items-center gap-2 flex-wrap">
          <CalendarClock className="w-4 h-4" style={{ color: colors.teal }} />
          {pinnedCountdowns.length === 0 ? (
            <span className="text-xs font-medium" style={{ color: colors.muted }}>
              No pinned exam target countdowns yet.
            </span>
          ) : (
            pinnedCountdowns.map((cd) => {
              const days = getDaysRemaining(cd.targetDateIso);
              return (
                <span
                  key={cd.id}
                  className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold"
                  style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                >
                  <span>{cd.title}</span>
                  <span style={{ color: days <= 7 ? colors.red : colors.teal }}>
                    {days > 0 ? `${days}d` : days === 0 ? 'Today' : 'Done'}
                  </span>
                </span>
              );
            })
          )}
        </div>
        <button
          type="button"
          onClick={onOpenCountdownsModal}
          className="px-3 py-1.5 rounded-lg text-xs font-bold border"
          style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.teal }}
        >
          Manage Targets
        </button>
      </div>

      {/* Header & Top Actions */}
      <div
        className="p-4 rounded-xl flex flex-col md:flex-row md:items-center justify-between gap-3"
        style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
      >
        <div>
          <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
            <FolderOpen className="w-5 h-5" style={{ color: colors.teal }} />
            Folders & Question Bank Library ({papers.length} sets)
          </h3>
          <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
            Organize nested subject folders, track mock completion, and run cohort exports
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => onOpenCreateModal(activeFolderId)}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold text-white shadow-sm"
            style={{ backgroundColor: colors.teal }}
          >
            <FilePlus2 className="w-4 h-4" />
            New Question Set
          </button>
          <button
            type="button"
            onClick={() => onOpenBatchImportModal(activeFolderId)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold border"
            style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.inkBold }}
          >
            <Layers className="w-3.5 h-3.5" style={{ color: colors.teal }} />
            Batch Import
          </button>
          <button
            type="button"
            onClick={() => setIsCreatingFolder((v) => !v)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold border"
            style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.ink }}
          >
            <FolderPlus className="w-3.5 h-3.5" style={{ color: colors.teal }} />
            New Subfolder
          </button>
        </div>
      </div>

      {/* Create Subfolder Inline Bar */}
      {isCreatingFolder && (
        <form
          onSubmit={handleCreateSubfolder}
          className="p-3.5 rounded-xl flex flex-wrap items-center gap-3"
          style={{ backgroundColor: colors.tealLight, border: `1px solid ${colors.teal}` }}
        >
          <input
            type="text"
            value={newFolderName}
            onChange={(e) => setNewFolderName(e.target.value)}
            placeholder={`Subfolder inside "${folderById.get(activeFolderId)?.name || 'Root'}"...`}
            className="flex-1 min-w-[200px] px-3 py-2 rounded-lg text-xs focus:outline-none"
            style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
          />
          <div className="flex items-center gap-1.5">
            {FOLDER_COLORS.map((hex) => (
              <button
                key={hex}
                type="button"
                onClick={() => setNewFolderColor(hex)}
                className="w-5 h-5 rounded-full border-2"
                style={{ backgroundColor: hex, borderColor: newFolderColor === hex ? '#FFFFFF' : 'transparent' }}
                aria-label={`Color ${hex}`}
              />
            ))}
          </div>
          <button type="submit" className="px-3.5 py-2 rounded-lg text-xs font-bold text-white" style={{ backgroundColor: colors.teal }}>
            Create
          </button>
          <button type="button" onClick={() => setIsCreatingFolder(false)} className="p-1.5" style={{ color: colors.muted }}>
            <X className="w-4 h-4" />
          </button>
        </form>
      )}

      {/* Breadcrumbs + Subfolders Grid */}
      <div className="space-y-3">
        <div className="flex items-center gap-1 flex-wrap text-xs">
          {breadcrumbs.map((bc, idx) => (
            <React.Fragment key={bc.id}>
              {idx > 0 && <ChevronRight className="w-3.5 h-3.5" style={{ color: colors.muted }} />}
              <button
                type="button"
                onClick={() => setActiveFolderId(bc.id)}
                className="px-2.5 py-1 rounded-lg font-bold transition"
                style={{
                  backgroundColor: bc.id === activeFolderId ? colors.navyBanner : colors.surfaceVariant,
                  color: bc.id === activeFolderId ? colors.navyBannerText : colors.ink,
                }}
              >
                {bc.name} ({countPapersRecursive(bc.id)})
              </button>
            </React.Fragment>
          ))}
        </div>

        {childFolders.length > 0 && !searchQuery.trim() && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {childFolders.map((f) => (
              <div
                key={f.id}
                onClick={() => setActiveFolderId(f.id)}
                className="p-3 rounded-xl flex items-center justify-between gap-2 cursor-pointer transition hover:shadow-sm"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div
                    className="w-8 h-8 rounded-lg flex items-center justify-center text-white flex-shrink-0"
                    style={{ backgroundColor: f.color }}
                  >
                    <Folder className="w-4 h-4" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                      {f.name}
                    </div>
                    <div className="text-[10.5px]" style={{ color: colors.muted }}>
                      {countPapersRecursive(f.id)} set(s)
                    </div>
                  </div>
                </div>
                {f.id !== 'default' && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (window.confirm(`Delete folder "${f.name}"? Contained papers will move to parent.`)) {
                        onDeleteFolder(f.id);
                        if (activeFolderId === f.id) setActiveFolderId(f.parentId || 'default');
                      }
                    }}
                    className="p-1.5 rounded hover:opacity-80"
                    style={{ color: colors.red }}
                    aria-label="Delete folder"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Search & Filters */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-2.5" style={{ color: colors.muted }} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Deep search title, subject, roll no., or inside questions..."
            className="w-full pl-9 pr-3 py-2 rounded-lg text-xs focus:outline-none"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
          />
        </div>

        <div className="flex items-center gap-1.5 flex-wrap text-xs">
          {(['ALL', 'PENDING', 'COMPLETED'] as const).map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => setStageFilter(st)}
              className="px-2.5 py-1.5 rounded-lg font-bold"
              style={{
                backgroundColor: stageFilter === st ? colors.teal : colors.surfaceVariant,
                color: stageFilter === st ? '#FFFFFF' : colors.ink,
              }}
            >
              {st === 'ALL' ? 'All Stages' : st === 'PENDING' ? 'Pending' : 'Completed'}
            </button>
          ))}
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value as PaperCategory | 'ALL')}
            className="px-2.5 py-1.5 rounded-lg font-bold"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
          >
            <option value="ALL">All Categories</option>
            <option value="FULL_MOCK">Full Mock</option>
            <option value="SUBJECT_TEST">Subject Test</option>
            <option value="TOPIC_DRILL">Topic Drill</option>
            <option value="PYQ">PYQ</option>
            <option value="REVISION">Revision</option>
          </select>
        </div>
      </div>

      {/* Batch Toolbar */}
      {selectedIds.length > 0 && (
        <div
          className="p-3 rounded-xl flex flex-wrap items-center justify-between gap-2 text-xs"
          style={{ backgroundColor: colors.tealLight, border: `1px solid ${colors.teal}` }}
        >
          <span className="font-bold" style={{ color: colors.tealDark }}>
            {selectedIds.length} paper(s) selected
          </span>
          <div className="flex items-center gap-2 flex-wrap">
            <select
              value={moveTargetFolderId}
              onChange={(e) => setMoveTargetFolderId(e.target.value)}
              className="px-2 py-1 rounded text-xs"
              style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            >
              {folders.map((f) => (
                <option key={f.id} value={f.id}>
                  Move to: {f.name}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => {
                onMovePapersToFolder(selectedIds, moveTargetFolderId);
                setSelectedIds([]);
              }}
              className="px-2.5 py-1 rounded font-bold text-white"
              style={{ backgroundColor: colors.teal }}
            >
              Move
            </button>
            <button
              type="button"
              onClick={handleExportSelectedBundle}
              className="px-2.5 py-1 rounded font-bold border"
              style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.ink }}
            >
              <Download className="w-3 h-3 inline mr-1" />
              Export Bundle
            </button>
            <button
              type="button"
              onClick={handleExportCohortCsv}
              className="px-2.5 py-1 rounded font-bold border"
              style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.ink }}
            >
              Cohort CSV
            </button>
            <button
              type="button"
              onClick={() => {
                if (window.confirm(`Delete ${selectedIds.length} selected paper(s)?`)) {
                  onDeletePapers(selectedIds);
                  setSelectedIds([]);
                }
              }}
              className="px-2.5 py-1 rounded font-bold text-white"
              style={{ backgroundColor: colors.red }}
            >
              Delete
            </button>
            <button type="button" onClick={() => setSelectedIds([])} className="font-semibold" style={{ color: colors.muted }}>
              Clear
            </button>
          </div>
        </div>
      )}

      {/* Papers List */}
      <div className="space-y-2.5">
        {filteredPapers.length === 0 ? (
          <div
            className="p-8 rounded-xl text-center space-y-2 border-2 border-dashed"
            style={{ borderColor: colors.border, backgroundColor: colors.surfaceVariant }}
          >
            <div className="text-sm font-bold" style={{ color: colors.inkBold }}>
              No question sets in this view
            </div>
            <p className="text-xs" style={{ color: colors.muted }}>
              Create a new question set or batch-import files into "{folderById.get(activeFolderId)?.name || 'Root'}".
            </p>
          </div>
        ) : (
          filteredPapers.map((p) => {
            const isActive = p.metadata.id === currentPaper.metadata.id;
            const isChecked = selectedIds.includes(p.metadata.id);
            const langs = PaperLanguageDetector.detectLanguages(
              p.questions.slice(0, 5).map((q) => q.question).join('\n'),
              p.metadata.title
            );

            return (
              <div
                key={p.metadata.id}
                className="p-4 rounded-xl flex flex-col md:flex-row md:items-center justify-between gap-3 transition"
                style={{
                  backgroundColor: isActive ? colors.tealLight : colors.surfaceVariant,
                  border: `1.5px solid ${isActive ? colors.teal : colors.border}`,
                }}
              >
                <div className="flex items-start gap-3 min-w-0 flex-1">
                  <button
                    type="button"
                    onClick={() => handleToggleSelect(p.metadata.id)}
                    className="mt-0.5 flex-shrink-0"
                    style={{ color: isChecked ? colors.teal : colors.muted }}
                    aria-label="Select paper"
                  >
                    {isChecked ? <CheckSquare className="w-4 h-4" /> : <Square className="w-4 h-4" />}
                  </button>
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-bold truncate" style={{ color: colors.inkBold }}>
                        {p.metadata.title}
                      </span>
                      {isActive && (
                        <span
                          className="px-2 py-0.5 rounded-full text-[10px] font-bold text-white"
                          style={{ backgroundColor: colors.teal }}
                        >
                          Active
                        </span>
                      )}
                      {p.metadata.isCompleted && (
                        <span
                          className="px-2 py-0.5 rounded-full text-[10px] font-bold"
                          style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
                        >
                          ✓ Completed
                        </span>
                      )}
                      {p.metadata.manualCategory && (
                        <span
                          className="px-2 py-0.5 rounded text-[10px] font-bold"
                          style={{ backgroundColor: colors.purpleLight, color: colors.purple }}
                        >
                          {p.metadata.manualCategory.replace('_', ' ')}
                        </span>
                      )}
                    </div>
                    <div className="text-xs flex flex-wrap items-center gap-2" style={{ color: colors.inkMuted }}>
                      <span>{p.metadata.subject}</span>
                      <span>•</span>
                      <span>{p.questions.length} Qs</span>
                      <span>•</span>
                      <span className="font-mono">Set {p.metadata.setNumber}</span>
                      <span>•</span>
                      <span className="font-mono">Roll {p.metadata.rollNumber || '0000000001'}</span>
                      <span>•</span>
                      <span>{langs.join('/')}</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 flex-wrap">
                  <button
                    type="button"
                    onClick={() => onTogglePaperCompleted(p.metadata.id)}
                    className="p-2 rounded-lg border text-xs"
                    title={p.metadata.isCompleted ? 'Mark Pending' : 'Mark Completed'}
                    style={{
                      borderColor: colors.border,
                      backgroundColor: p.metadata.isCompleted ? colors.greenLight : colors.surface,
                      color: p.metadata.isCompleted ? colors.green : colors.muted,
                    }}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => onOpenEditMetadata(p)}
                    className="p-2 rounded-lg border text-xs"
                    title="Edit Metadata & Marking"
                    style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.ink }}
                  >
                    <Sliders className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      onSelectPaper(p);
                      onNavigateToMaster();
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-bold border"
                    style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.inkBold }}
                  >
                    Inspect
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      onSelectPaper(p);
                      onNavigateToStudentPortal();
                    }}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold text-white"
                    style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                  >
                    <Play className="w-3 h-3" />
                    Practice
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default FolderManagerTab;
