import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  BookOpen,
  Check,
  ChevronRight,
  Edit3,
  FilePlus2,
  FolderInput,
  FolderPlus,
  Layers,
  Mic,
  Play,
  Plus,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import { FolderItem } from '../types/exam';
import {
  FlashcardSrsState,
  KNOWLEDGE_COLLECTION_META,
  KnowledgeCollectionType,
  KnowledgeCoursesMap,
  StudyNoteRecord,
} from '../types/phase4';
import { SpacedRepetitionEngine } from '../utils/spacedRepetitionEngine';
import { StudyNoteParser } from '../utils/studyNoteParser';

export const DEFAULT_UNCATEGORIZED_COURSE = 'General / Uncategorized';

export interface KnowledgeCourseBrowserProps {
  notes: StudyNoteRecord[];
  folders: FolderItem[];
  registeredCoursesMap: KnowledgeCoursesMap;
  selectedCourse: string | null;
  selectedSubject: string | null;
  searchQuery: string;
  filterType: KnowledgeCollectionType | 'ALL';
  onSelectCourse: (course: string | null) => void;
  onSelectSubject: (subject: string | null) => void;
  onSearchQueryChange: (q: string) => void;
  onFilterTypeChange: (t: KnowledgeCollectionType | 'ALL') => void;
  onCreateCourse: (courseName: string) => void;
  onAddSubjectToCourse: (courseName: string, subjectName: string) => void;
  onRenameCourse: (oldCourse: string, newCourse: string) => void;
  onRenameSubject: (courseName: string, oldSubject: string, newSubject: string) => void;
  onAssignNoteCourseSubject: (noteId: string, courseName: string, subjectName: string) => void;
  onOpenStudy: (note: StudyNoteRecord) => void;
  onOpenSm2: (note: StudyNoteRecord) => void;
  onOpenAppend: (note: StudyNoteRecord) => void;
  onOpenEditor: (note: StudyNoteRecord | null, prefill?: { course?: string; subject?: string }) => void;
  onSendNoteToDictation: (noteId: string) => void;
  onDeleteNote: (noteId: string) => void;
}

interface NoteMetrics {
  unitCount: number;
  masteredCount: number;
  dueSrsCount: number;
  newSrsCount: number;
}

export const KnowledgeCourseBrowser: React.FC<KnowledgeCourseBrowserProps> = ({
  notes,
  registeredCoursesMap,
  selectedCourse,
  selectedSubject,
  searchQuery,
  filterType,
  onSelectCourse,
  onSelectSubject,
  onSearchQueryChange,
  onFilterTypeChange,
  onCreateCourse,
  onAddSubjectToCourse,
  onRenameCourse,
  onRenameSubject,
  onAssignNoteCourseSubject,
  onOpenStudy,
  onOpenSm2,
  onOpenAppend,
  onOpenEditor,
  onSendNoteToDictation,
  onDeleteNote,
}) => {
  const { colors } = useTheme();
  const [showAddCourse, setShowAddCourse] = useState(false);
  const [newCourseName, setNewCourseName] = useState('');
  const [addSubjectForCourse, setAddSubjectForCourse] = useState<string | null>(null);
  const [newSubjectName, setNewSubjectName] = useState('');
  const [renameCourseTarget, setRenameCourseTarget] = useState<string | null>(null);
  const [renameCourseInput, setRenameCourseInput] = useState('');
  const [renameSubjectTarget, setRenameSubjectTarget] = useState<{ course: string; subject: string } | null>(null);
  const [renameSubjectInput, setRenameSubjectInput] = useState('');
  const [moveNoteTarget, setMoveNoteTarget] = useState<StudyNoteRecord | null>(null);
  const [moveCourseInput, setMoveCourseInput] = useState('');
  const [moveSubjectInput, setMoveSubjectInput] = useState('');
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

  const metricsByNoteId = useMemo(() => {
    const map = new Map<string, NoteMetrics>();
    for (const note of notes) {
      const blocks = StudyNoteParser.parseBlocks(note.rawContent, note.formatRule);
      const units = blocks.filter(
        (b) => b.kind === 'one_liner' || b.kind === 'multi_liner_card'
      );
      const unitIndexSet = new Set(units.map((u) => u.blockIndex));
      const masteredCount = (note.masteredIndices || []).filter((idx) =>
        unitIndexSet.has(idx)
      ).length;
      const srsStr: Record<string, FlashcardSrsState | undefined> = {};
      for (const [k, v] of Object.entries(note.srsStates || {})) {
        srsStr[String(k)] = v;
      }
      const srsStats = SpacedRepetitionEngine.computeDeckStats(
        units,
        (u) => u.blockIndex,
        srsStr
      );
      map.set(note.id, {
        unitCount: units.length,
        masteredCount,
        dueSrsCount: srsStats.dueCount,
        newSrsCount: srsStats.newCount,
      });
    }
    return map;
  }, [notes]);

  const effectiveCourseOf = (note: StudyNoteRecord) =>
    (note.course || '').trim() || DEFAULT_UNCATEGORIZED_COURSE;

  const effectiveSubjectOf = (note: StudyNoteRecord) =>
    (note.subject || '').trim() || 'General Studies';

  // Suggested Next Study note (prioritizes Due SRS -> Partial Mastery -> Unstudied)
  const suggestedNote = useMemo(() => {
    if (notes.length === 0) return null;
    const scored = notes
      .map((note) => {
        const m = metricsByNoteId.get(note.id) || {
          unitCount: 0,
          masteredCount: 0,
          dueSrsCount: 0,
          newSrsCount: 0,
        };
        let score = 0;
        if (m.dueSrsCount > 0) score += 1000 + m.dueSrsCount * 10;
        else if (m.unitCount > 0 && m.masteredCount > 0 && m.masteredCount < m.unitCount) score += 500 + (m.unitCount - m.masteredCount);
        else if (m.unitCount > 0 && m.masteredCount === 0) score += 200 + Math.min(50, m.unitCount);
        return { note, m, score };
      })
      .sort((a, b) => b.score - a.score || b.note.updatedAt - a.note.updatedAt);
    return scored[0] || null;
  }, [notes, metricsByNoteId]);

  // Build hierarchical Course -> Subject -> Notes structure including empty registered shells
  const courseBuckets = useMemo(() => {
    const courseMap = new Map<
      string,
      {
        courseName: string;
        subjectMap: Map<string, StudyNoteRecord[]>;
      }
    >();

    const ensureCourse = (courseName: string) => {
      const cTrim = courseName.trim() || DEFAULT_UNCATEGORIZED_COURSE;
      const cKey = cTrim.toLowerCase();
      for (const existingKey of courseMap.keys()) {
        if (existingKey.toLowerCase() === cKey) return courseMap.get(existingKey)!;
      }
      const bucket = { courseName: cTrim, subjectMap: new Map<string, StudyNoteRecord[]>() };
      courseMap.set(cTrim, bucket);
      return bucket;
    };

    const ensureSubject = (courseBucket: { subjectMap: Map<string, StudyNoteRecord[]> }, subjectName: string) => {
      const sTrim = subjectName.trim() || 'General Studies';
      const sKey = sTrim.toLowerCase();
      for (const existingKey of courseBucket.subjectMap.keys()) {
        if (existingKey.toLowerCase() === sKey) return courseBucket.subjectMap.get(existingKey)!;
      }
      const arr: StudyNoteRecord[] = [];
      courseBucket.subjectMap.set(sTrim, arr);
      return arr;
    };

    for (const [cName, subjects] of Object.entries(registeredCoursesMap || {})) {
      const cBucket = ensureCourse(cName);
      for (const sub of subjects || []) {
        ensureSubject(cBucket, sub);
      }
    }

    for (const note of notes) {
      if (filterType !== 'ALL' && note.collectionType !== filterType) continue;
      const cBucket = ensureCourse(effectiveCourseOf(note));
      const arr = ensureSubject(cBucket, effectiveSubjectOf(note));
      arr.push(note);
    }

    return Array.from(courseMap.values())
      .map((cb) => {
        const subjects = Array.from(cb.subjectMap.entries())
          .map(([subjectName, subNotes]) => {
            const sortedNotes = [...subNotes].sort((a, b) => b.updatedAt - a.updatedAt);
            let totalUnits = 0;
            let totalMastered = 0;
            let dueSrs = 0;
            for (const n of sortedNotes) {
              const m = metricsByNoteId.get(n.id);
              if (m) {
                totalUnits += m.unitCount;
                totalMastered += m.masteredCount;
                dueSrs += m.dueSrsCount;
              }
            }
            return {
              courseName: cb.courseName,
              subjectName,
              notes: sortedNotes,
              totalUnits,
              totalMastered,
              dueSrs,
            };
          })
          .sort((a, b) => a.subjectName.localeCompare(b.subjectName));

        const totalNotes = subjects.reduce((s, sub) => s + sub.notes.length, 0);
        const totalUnits = subjects.reduce((s, sub) => s + sub.totalUnits, 0);
        const totalMastered = subjects.reduce((s, sub) => s + sub.totalMastered, 0);
        const dueSrs = subjects.reduce((s, sub) => s + sub.dueSrs, 0);

        return {
          courseName: cb.courseName,
          subjects,
          totalNotes,
          totalUnits,
          totalMastered,
          dueSrs,
        };
      })
      .sort((a, b) => {
        if (a.courseName === DEFAULT_UNCATEGORIZED_COURSE && b.courseName !== DEFAULT_UNCATEGORIZED_COURSE) return 1;
        if (b.courseName === DEFAULT_UNCATEGORIZED_COURSE && a.courseName !== DEFAULT_UNCATEGORIZED_COURSE) return -1;
        return a.courseName.localeCompare(b.courseName);
      });
  }, [notes, registeredCoursesMap, filterType, metricsByNoteId]);

  const activeCourseBucket = useMemo(
    () =>
      selectedCourse
        ? courseBuckets.find((c) => c.courseName.toLowerCase() === selectedCourse.toLowerCase()) || null
        : null,
    [courseBuckets, selectedCourse]
  );

  const activeSubjectBucket = useMemo(
    () =>
      activeCourseBucket && selectedSubject
        ? activeCourseBucket.subjects.find((s) => s.subjectName.toLowerCase() === selectedSubject.toLowerCase()) || null
        : null,
    [activeCourseBucket, selectedSubject]
  );

  const flatFilteredNotes = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q && filterType === 'ALL') return null;
    if (!q && selectedCourse && selectedSubject) return null;
    if (!q) return null;
    return notes
      .filter((n) => filterType === 'ALL' || n.collectionType === filterType)
      .filter(
        (n) =>
          n.title.toLowerCase().includes(q) ||
          n.subject.toLowerCase().includes(q) ||
          (n.course || '').toLowerCase().includes(q) ||
          n.rawContent.toLowerCase().includes(q)
      )
      .sort((a, b) => b.updatedAt - a.updatedAt);
  }, [notes, searchQuery, filterType, selectedCourse, selectedSubject]);

  const renderNoteCard = (note: StudyNoteRecord) => {
    const m = metricsByNoteId.get(note.id) || {
      unitCount: 0,
      masteredCount: 0,
      dueSrsCount: 0,
      newSrsCount: 0,
    };
    const meta = KNOWLEDGE_COLLECTION_META[note.collectionType];
    const progressPct = m.unitCount > 0 ? Math.round((m.masteredCount / m.unitCount) * 100) : 0;

    return (
      <div
        key={note.id}
        className="rounded-xl border p-4 flex flex-col justify-between gap-3 transition"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <span
              className="px-2 py-0.5 rounded-md text-[0.625rem] font-black border"
              style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
            >
              {note.collectionType === 'CUSTOM' && note.customLabel ? note.customLabel : meta.shortBadge}
            </span>
            <span className="text-[0.6875rem] font-semibold truncate" style={{ color: colors.muted }}>
              {effectiveCourseOf(note)} • {effectiveSubjectOf(note)}
            </span>
          </div>

          <button
            type="button"
            onClick={() => onOpenStudy(note)}
            className="text-left w-full"
          >
            <h4 className="text-sm font-black line-clamp-2 hover:underline" style={{ color: colors.inkBold }}>
              {note.title}
            </h4>
          </button>

          {m.unitCount > 0 && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-[0.625rem] font-bold" style={{ color: colors.muted }}>
                <span>
                  Mastery: {m.masteredCount}/{m.unitCount} ({progressPct}%)
                </span>
                {m.dueSrsCount > 0 && (
                  <span style={{ color: colors.red }}>{m.dueSrsCount} Due SRS</span>
                )}
              </div>
              <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: colors.surfaceVariant }}>
                <div
                  className="h-full transition-all"
                  style={{ width: `${progressPct}%`, backgroundColor: colors.green }}
                />
              </div>
            </div>
          )}

          <div className="flex flex-wrap gap-1.5 text-[0.625rem] font-bold">
            <span
              className="px-2 py-0.5 rounded"
              style={{ backgroundColor: colors.surfaceVariant, color: colors.ink }}
            >
              {m.unitCount} Recall Items
            </span>
            {m.dueSrsCount + m.newSrsCount > 0 && (
              <span
                className="px-2 py-0.5 rounded"
                style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}
              >
                {m.dueSrsCount + m.newSrsCount} Due/New
              </span>
            )}
          </div>
        </div>

        <div
          className="pt-3 border-t flex flex-wrap items-center justify-between gap-1.5 text-xs"
          style={{ borderColor: colors.borderLight }}
        >
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => onOpenStudy(note)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              <Play className="w-3 h-3" />
              Study
            </button>
            <button
              type="button"
              onClick={() => onOpenSm2(note)}
              className="px-2.5 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.tealDark }}
            >
              SM-2
            </button>
            <button
              type="button"
              onClick={() => onOpenAppend(note)}
              className="flex items-center gap-1 px-2 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }}
              title="Append new items non-destructively"
            >
              <FilePlus2 className="w-3 h-3" />
              Append
            </button>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => {
                setMoveNoteTarget(note);
                setMoveCourseInput((note.course || '').trim());
                setMoveSubjectInput((note.subject || '').trim());
              }}
              className="p-1.5 rounded-lg border"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
              title="Move to Course / Subject"
            >
              <FolderInput className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={() => onSendNoteToDictation(note.id)}
              className="p-1.5 rounded-lg border"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
              title="Dictate this note"
            >
              <Mic className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={() => onOpenEditor(note)}
              className="p-1.5 rounded-lg border"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
              title="Edit note"
            >
              <Edit3 className="w-3.5 h-3.5" />
            </button>
            {pendingDeleteId === note.id ? (
              <button
                type="button"
                onClick={() => {
                  onDeleteNote(note.id);
                  setPendingDeleteId(null);
                }}
                className="px-2 py-1 rounded-lg font-bold text-white text-[0.625rem]"
                style={{ backgroundColor: colors.red }}
              >
                Confirm
              </button>
            ) : (
              <button
                type="button"
                onClick={() => setPendingDeleteId(note.id)}
                className="p-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.red }}
                title="Delete note"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-4">
      {/* Suggested Next Study Card at Root */}
      {!selectedCourse && !searchQuery.trim() && suggestedNote && (
        <div
          className="p-4 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.teal }}
        >
          <div className="min-w-0">
            <div className="text-[0.625rem] font-black uppercase tracking-wider opacity-85" style={{ color: colors.navyBannerText }}>
              {suggestedNote.m.dueSrsCount > 0
                ? `Due for Revision (${suggestedNote.m.dueSrsCount} SRS Cards)`
                : suggestedNote.m.masteredCount > 0 && suggestedNote.m.masteredCount < suggestedNote.m.unitCount
                ? 'Continue Studying'
                : 'Suggested Next Study'}
            </div>
            <div className="text-sm sm:text-base font-black truncate mt-0.5" style={{ color: colors.navyBannerText }}>
              {suggestedNote.note.title}
            </div>
            <div className="text-[0.6875rem] opacity-85 mt-0.5" style={{ color: colors.navyBannerText }}>
              {effectiveCourseOf(suggestedNote.note)} • {effectiveSubjectOf(suggestedNote.note)} •{' '}
              {suggestedNote.m.masteredCount}/{suggestedNote.m.unitCount} Mastered
            </div>
          </div>

          <div className="flex items-center gap-2 flex-shrink-0">
            <button
              type="button"
              onClick={() => onOpenStudy(suggestedNote.note)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-black shadow"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              <Play className="w-3.5 h-3.5" />
              {suggestedNote.m.dueSrsCount > 0 ? 'Study & Review' : 'Open Study'}
            </button>
          </div>
        </div>
      )}

      {/* Search & Filter Row */}
      <div className="flex flex-col md:flex-row gap-3">
        <div className="relative flex-1">
          <Search
            className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2"
            style={{ color: colors.muted }}
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchQueryChange(e.target.value)}
            placeholder="Search courses, subjects, notes, formulas, topics..."
            className="w-full pl-9 pr-8 py-2 rounded-xl border text-xs"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => onSearchQueryChange('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2"
              style={{ color: colors.muted }}
              aria-label="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        <select
          value={filterType}
          onChange={(e) => onFilterTypeChange(e.target.value as KnowledgeCollectionType | 'ALL')}
          className="px-3 py-2 rounded-xl border text-xs font-bold"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
        >
          <option value="ALL">All Collection Types ({notes.length})</option>
          {(Object.keys(KNOWLEDGE_COLLECTION_META) as KnowledgeCollectionType[]).map((key) => (
            <option key={key} value={key}>
              {KNOWLEDGE_COLLECTION_META[key].label}
            </option>
          ))}
        </select>
      </div>

      {/* Move Note Course/Subject Modal */}
      {moveNoteTarget && (
        <div
          className="p-4 rounded-xl border space-y-3"
          style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
        >
          <div className="flex items-center justify-between gap-2">
            <div className="text-xs font-black" style={{ color: colors.tealDark }}>
              Move "{moveNoteTarget.title}" to Course &amp; Subject
            </div>
            <button type="button" onClick={() => setMoveNoteTarget(null)} style={{ color: colors.ink }}>
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
            <input
              type="text"
              value={moveCourseInput}
              onChange={(e) => setMoveCourseInput(e.target.value)}
              placeholder="Course name (leave empty for General)"
              list="examset-knowledge-course-list"
              className="px-3 py-2 rounded-lg border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            />
            <input
              type="text"
              value={moveSubjectInput}
              onChange={(e) => setMoveSubjectInput(e.target.value)}
              placeholder="Subject name"
              className="px-3 py-2 rounded-lg border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            />
          </div>
          <datalist id="examset-knowledge-course-list">
            {Object.keys(registeredCoursesMap).map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                onAssignNoteCourseSubject(
                  moveNoteTarget.id,
                  moveCourseInput.trim(),
                  moveSubjectInput.trim() || 'General Studies'
                );
                setMoveNoteTarget(null);
              }}
              className="px-3.5 py-1.5 rounded-lg text-xs font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              Save Course &amp; Subject
            </button>
          </div>
        </div>
      )}

      {/* Unified Search Results */}
      {flatFilteredNotes !== null ? (
        flatFilteredNotes.length === 0 ? (
          <div
            className="p-8 rounded-xl border text-center space-y-2"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <BookOpen className="w-8 h-8 mx-auto" style={{ color: colors.muted }} />
            <p className="text-xs font-bold" style={{ color: colors.inkBold }}>
              No study notes match "{searchQuery}".
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {flatFilteredNotes.map(renderNoteCard)}
          </div>
        )
      ) : activeSubjectBucket ? (
        /* LEVEL 2: Notes inside Course > Subject */
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 flex-wrap text-xs">
              <button
                type="button"
                onClick={() => {
                  onSelectSubject(null);
                }}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                {activeCourseBucket?.courseName}
              </button>
              <ChevronRight className="w-3.5 h-3.5" style={{ color: colors.muted }} />
              <span
                className="px-2.5 py-1.5 rounded-lg font-black"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
              >
                {activeSubjectBucket.subjectName} ({activeSubjectBucket.notes.length})
              </span>
            </div>

            <button
              type="button"
              onClick={() =>
                onOpenEditor(null, {
                  course:
                    activeSubjectBucket.courseName === DEFAULT_UNCATEGORIZED_COURSE
                      ? ''
                      : activeSubjectBucket.courseName,
                  subject: activeSubjectBucket.subjectName,
                })
              }
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              <Plus className="w-3.5 h-3.5" />
              New Note in {activeSubjectBucket.subjectName}
            </button>
          </div>

          {activeSubjectBucket.notes.length === 0 ? (
            <div
              className="p-8 rounded-xl border text-center space-y-2"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <p className="text-xs font-bold" style={{ color: colors.inkBold }}>
                No notes in "{activeSubjectBucket.subjectName}" yet.
              </p>
              <button
                type="button"
                onClick={() =>
                  onOpenEditor(null, {
                    course:
                      activeSubjectBucket.courseName === DEFAULT_UNCATEGORIZED_COURSE
                        ? ''
                        : activeSubjectBucket.courseName,
                    subject: activeSubjectBucket.subjectName,
                  })
                }
                className="px-3.5 py-1.5 rounded-lg text-xs font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Add First Study Note
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {activeSubjectBucket.notes.map(renderNoteCard)}
            </div>
          )}
        </div>
      ) : activeCourseBucket ? (
        /* LEVEL 1: Subjects inside Course */
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 flex-wrap text-xs">
              <button
                type="button"
                onClick={() => {
                  onSelectSubject(null);
                  onSelectCourse(null);
                }}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                All Courses
              </button>
              <ChevronRight className="w-3.5 h-3.5" style={{ color: colors.muted }} />
              <span
                className="px-2.5 py-1.5 rounded-lg font-black"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
              >
                {activeCourseBucket.courseName} ({activeCourseBucket.subjects.length} Subjects)
              </span>
            </div>

            <div className="flex items-center gap-2">
              {activeCourseBucket.courseName !== DEFAULT_UNCATEGORIZED_COURSE && (
                <button
                  type="button"
                  onClick={() => {
                    setAddSubjectForCourse(activeCourseBucket.courseName);
                    setNewSubjectName('');
                  }}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-bold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <FolderPlus className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                  Add Subject
                </button>
              )}
              <button
                type="button"
                onClick={() =>
                  onOpenEditor(null, {
                    course:
                      activeCourseBucket.courseName === DEFAULT_UNCATEGORIZED_COURSE
                        ? ''
                        : activeCourseBucket.courseName,
                  })
                }
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Plus className="w-3.5 h-3.5" />
                New Note
              </button>
            </div>
          </div>

          {addSubjectForCourse === activeCourseBucket.courseName && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!newSubjectName.trim()) return;
                onAddSubjectToCourse(activeCourseBucket.courseName, newSubjectName.trim());
                setNewSubjectName('');
                setAddSubjectForCourse(null);
              }}
              className="p-3 rounded-xl border flex flex-wrap items-center gap-2 text-xs"
              style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
            >
              <input
                type="text"
                value={newSubjectName}
                onChange={(e) => setNewSubjectName(e.target.value)}
                placeholder={`New subject inside "${activeCourseBucket.courseName}"...`}
                className="flex-1 min-w-[200px] px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              />
              <button
                type="submit"
                className="px-3 py-2 rounded-lg font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Add Subject
              </button>
              <button type="button" onClick={() => setAddSubjectForCourse(null)} style={{ color: colors.muted }}>
                <X className="w-4 h-4" />
              </button>
            </form>
          )}

          {renameSubjectTarget && renameSubjectTarget.course === activeCourseBucket.courseName && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!renameSubjectInput.trim()) return;
                onRenameSubject(
                  renameSubjectTarget.course,
                  renameSubjectTarget.subject,
                  renameSubjectInput.trim()
                );
                setRenameSubjectTarget(null);
              }}
              className="p-3 rounded-xl border flex flex-wrap items-center gap-2 text-xs"
              style={{ backgroundColor: colors.amberLight, borderColor: colors.amber }}
            >
              <span className="font-bold" style={{ color: colors.inkBold }}>
                Rename "{renameSubjectTarget.subject}" to:
              </span>
              <input
                type="text"
                value={renameSubjectInput}
                onChange={(e) => setRenameSubjectInput(e.target.value)}
                className="flex-1 min-w-[180px] px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              />
              <button
                type="submit"
                className="px-3 py-1.5 rounded-lg font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Save
              </button>
              <button type="button" onClick={() => setRenameSubjectTarget(null)} style={{ color: colors.muted }}>
                <X className="w-4 h-4" />
              </button>
            </form>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5">
            {activeCourseBucket.subjects.map((sub) => {
              const pct = sub.totalUnits > 0 ? Math.round((sub.totalMastered / sub.totalUnits) * 100) : 0;
              return (
                <div
                  key={sub.subjectName}
                  onClick={() => onSelectSubject(sub.subjectName)}
                  className="p-4 rounded-xl border cursor-pointer transition hover:shadow-sm flex flex-col justify-between gap-2.5"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h4 className="text-sm font-black truncate" style={{ color: colors.inkBold }}>
                        {sub.subjectName}
                      </h4>
                      <div className="text-[0.6875rem] mt-0.5" style={{ color: colors.muted }}>
                        {sub.notes.length} Set(s) • {sub.totalUnits} Recall Items
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setRenameSubjectTarget({ course: sub.courseName, subject: sub.subjectName });
                        setRenameSubjectInput(sub.subjectName);
                      }}
                      className="p-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
                      title="Rename Subject"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {sub.totalUnits > 0 && (
                    <div className="space-y-1">
                      <div className="flex items-center justify-between text-[0.625rem] font-bold" style={{ color: colors.muted }}>
                        <span>
                          {sub.totalMastered}/{sub.totalUnits} Mastered ({pct}%)
                        </span>
                        {sub.dueSrs > 0 && <span style={{ color: colors.red }}>{sub.dueSrs} Due</span>}
                      </div>
                      <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: colors.surfaceVariant }}>
                        <div className="h-full" style={{ width: `${pct}%`, backgroundColor: colors.green }} />
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        /* LEVEL 0: Courses Root */
        <div className="space-y-3.5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="text-xs font-black flex items-center gap-1.5" style={{ color: colors.inkBold }}>
              <Layers className="w-4 h-4" style={{ color: colors.teal }} />
              Knowledge Courses ({courseBuckets.length})
            </div>
            <button
              type="button"
              onClick={() => setShowAddCourse((v) => !v)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <FolderPlus className="w-3.5 h-3.5" style={{ color: colors.teal }} />
              New Course
            </button>
          </div>

          {showAddCourse && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!newCourseName.trim()) return;
                onCreateCourse(newCourseName.trim());
                setNewCourseName('');
                setShowAddCourse(false);
              }}
              className="p-3 rounded-xl border flex flex-wrap items-center gap-2 text-xs"
              style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
            >
              <input
                type="text"
                value={newCourseName}
                onChange={(e) => setNewCourseName(e.target.value)}
                placeholder="Course name (e.g., UPSC CSE, JEE Physics, Constitutional Law)..."
                className="flex-1 min-w-[220px] px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              />
              <button
                type="submit"
                className="px-3.5 py-2 rounded-lg font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Create Course
              </button>
              <button type="button" onClick={() => setShowAddCourse(false)} style={{ color: colors.muted }}>
                <X className="w-4 h-4" />
              </button>
            </form>
          )}

          {renameCourseTarget && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!renameCourseInput.trim()) return;
                onRenameCourse(renameCourseTarget, renameCourseInput.trim());
                setRenameCourseTarget(null);
              }}
              className="p-3 rounded-xl border flex flex-wrap items-center gap-2 text-xs"
              style={{ backgroundColor: colors.amberLight, borderColor: colors.amber }}
            >
              <span className="font-bold" style={{ color: colors.inkBold }}>
                Rename Course "{renameCourseTarget}" to:
              </span>
              <input
                type="text"
                value={renameCourseInput}
                onChange={(e) => setRenameCourseInput(e.target.value)}
                className="flex-1 min-w-[200px] px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              />
              <button
                type="submit"
                className="px-3 py-1.5 rounded-lg font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Check className="w-3.5 h-3.5 inline mr-1" />
                Save
              </button>
              <button type="button" onClick={() => setRenameCourseTarget(null)} style={{ color: colors.muted }}>
                <X className="w-4 h-4" />
              </button>
            </form>
          )}

          {courseBuckets.length === 0 ? (
            <div
              className="p-10 rounded-xl border text-center space-y-3"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <BookOpen className="w-9 h-9 mx-auto" style={{ color: colors.muted }} />
              <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
                No Knowledge Courses or Study Notes yet.
              </p>
              <button
                type="button"
                onClick={() => onOpenEditor(null)}
                className="px-4 py-2 rounded-lg text-xs font-bold"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Create First Study Note
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {courseBuckets.map((cb) => {
                const pct = cb.totalUnits > 0 ? Math.round((cb.totalMastered / cb.totalUnits) * 100) : 0;
                const isDefault = cb.courseName === DEFAULT_UNCATEGORIZED_COURSE;
                return (
                  <div
                    key={cb.courseName}
                    onClick={() => {
                      onSelectCourse(cb.courseName);
                      onSelectSubject(null);
                    }}
                    className="p-4 rounded-xl border cursor-pointer transition hover:shadow-sm flex flex-col justify-between gap-3"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border }}
                  >
                    <div className="space-y-2">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <span
                            className="inline-block px-2 py-0.5 rounded-md text-[0.625rem] font-black border mb-1"
                            style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                          >
                            {cb.subjects.length} Subject(s)
                          </span>
                          <h4 className="text-sm font-black truncate" style={{ color: colors.inkBold }}>
                            {cb.courseName}
                          </h4>
                        </div>
                        {!isDefault && (
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setRenameCourseTarget(cb.courseName);
                              setRenameCourseInput(cb.courseName);
                            }}
                            className="p-1.5 rounded-lg border"
                            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
                            title="Rename Course"
                          >
                            <Edit3 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>

                      <div className="flex flex-wrap gap-1.5 text-[0.625rem] font-bold">
                        <span className="px-2 py-0.5 rounded" style={{ backgroundColor: colors.surfaceVariant, color: colors.ink }}>
                          {cb.totalNotes} Note Set(s)
                        </span>
                        <span className="px-2 py-0.5 rounded" style={{ backgroundColor: colors.surfaceVariant, color: colors.ink }}>
                          {cb.totalUnits} Items
                        </span>
                        {cb.dueSrs > 0 && (
                          <span className="px-2 py-0.5 rounded" style={{ backgroundColor: colors.redLight, color: colors.red }}>
                            {cb.dueSrs} Due SRS
                          </span>
                        )}
                      </div>
                    </div>

                    {cb.totalUnits > 0 && (
                      <div className="space-y-1">
                        <div className="flex items-center justify-between text-[0.625rem] font-bold" style={{ color: colors.muted }}>
                          <span>Mastery Progress</span>
                          <span>{pct}%</span>
                        </div>
                        <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: colors.surfaceVariant }}>
                          <div className="h-full" style={{ width: `${pct}%`, backgroundColor: colors.green }} />
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default KnowledgeCourseBrowser;
