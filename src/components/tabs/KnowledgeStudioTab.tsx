import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  BookOpen,
  Check,
  CheckCircle2,
  Edit3,
  Eye,
  EyeOff,
  FilePlus2,
  FileWarning,
  Grid,
  Layers,
  Mic,
  Plus,
  Printer,
  Star,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import { AttemptRecord, FolderItem, QuestionPaper } from '../../types/exam';
import {
  DEFAULT_KNOWLEDGE_RULE,
  FlashcardSrsState,
  KNOWLEDGE_COLLECTION_META,
  KnowledgeCollectionType,
  KnowledgeCoursesMap,
  StudyNoteBlock,
  StudyNoteRecord,
} from '../../types/phase4';
import { StudyNoteParser } from '../../utils/studyNoteParser';
import { AppendKnowledgeModal } from '../AppendKnowledgeModal';
import { FlashcardDeckView } from '../FlashcardDeckView';
import {
  DEFAULT_UNCATEGORIZED_COURSE,
  KnowledgeCourseBrowser,
} from '../KnowledgeCourseBrowser';
import { KnowledgeNavigatorSheet } from '../KnowledgeNavigatorSheet';
import { LatexText } from '../LatexText';
import { MarkdownTableCard } from '../MarkdownTableCard';

type ReaderMaskMode =
  | 'NONE'
  | 'HIDE_DETAIL'
  | 'HIDE_EXPLANATION'
  | 'HIDE_LEAD'
  | 'CLUE_TO_TARGET';

type ReaderUnitFilter = 'ALL' | 'UNMASTERED' | 'STARRED';

interface KnowledgeStudioTabProps {
  notes: StudyNoteRecord[];
  papers: QuestionPaper[];
  attempts: AttemptRecord[];
  folders: FolderItem[];
  registeredCoursesMap: KnowledgeCoursesMap;
  initialSelectedNoteId?: string | null;
  onClearInitialSelectedNoteId?: () => void;
  onSaveCoursesMap: (nextMap: KnowledgeCoursesMap) => void;
  onSaveNote: (note: StudyNoteRecord) => void;
  onSaveNotesBatch?: (nextNotes: StudyNoteRecord[], msg?: string) => void;
  onDeleteNote: (noteId: string) => void;
  onOpenEditor: (
    note?: StudyNoteRecord | null,
    prefill?: { course?: string; subject?: string }
  ) => void;
  onSendNoteToDictation: (noteId: string) => void;
  onNotify?: (msg: string) => void;
  registerBackHandler?: (handler: (() => boolean) | null) => void;
}

export const KnowledgeStudioTab: React.FC<KnowledgeStudioTabProps> = ({
  notes,
  papers,
  attempts,
  folders,
  registeredCoursesMap,
  initialSelectedNoteId,
  onClearInitialSelectedNoteId,
  onSaveCoursesMap,
  onSaveNote,
  onSaveNotesBatch,
  onDeleteNote,
  onOpenEditor,
  onSendNoteToDictation,
  onNotify,
  registerBackHandler,
}) => {
  const { colors, scalePx } = useTheme();
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [selectedCourse, setSelectedCourse] = useState<string | null>(null);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'READER' | 'FLASHCARDS'>('READER');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterType, setFilterType] = useState<KnowledgeCollectionType | 'ALL'>('ALL');
  const [maskMode, setMaskMode] = useState<ReaderMaskMode>('NONE');
  const [revealedIndices, setRevealedIndices] = useState<Record<number, boolean>>({});
  const [unitFilter, setUnitFilter] = useState<ReaderUnitFilter>('ALL');
  const [showWeakCompiler, setShowWeakCompiler] = useState<boolean>(false);
  const [showNavigator, setShowNavigator] = useState<boolean>(false);
  const [appendTargetNote, setAppendTargetNote] = useState<StudyNoteRecord | null>(null);
  const [highlightedBlockIndex, setHighlightedBlockIndex] = useState<number | null>(null);
  const readerTopRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (initialSelectedNoteId) {
      const target = notes.find((n) => n.id === initialSelectedNoteId);
      if (target) {
        setSelectedNoteId(target.id);
        setViewMode('READER');
        setSelectedCourse((target.course || '').trim() || DEFAULT_UNCATEGORIZED_COURSE);
        setSelectedSubject((target.subject || '').trim() || 'General Studies');
      }
      onClearInitialSelectedNoteId?.();
    }
  }, [initialSelectedNoteId, notes, onClearInitialSelectedNoteId]);

  const selectedNote = useMemo(
    () => notes.find((n) => n.id === selectedNoteId) || null,
    [notes, selectedNoteId]
  );

  // Register hierarchical Back Handler with App shell
  useEffect(() => {
    if (!registerBackHandler) return;
    const hasSubBack = Boolean(
      showNavigator ||
        appendTargetNote ||
        selectedNote ||
        showWeakCompiler ||
        searchQuery.trim() ||
        selectedSubject ||
        selectedCourse
    );
    if (!hasSubBack) {
      registerBackHandler(null);
      return;
    }
    registerBackHandler(() => {
      if (showNavigator) {
        setShowNavigator(false);
        return true;
      }
      if (appendTargetNote) {
        setAppendTargetNote(null);
        return true;
      }
      if (selectedNote) {
        if (viewMode === 'FLASHCARDS') {
          setViewMode('READER');
          return true;
        }
        setSelectedNoteId(null);
        setRevealedIndices({});
        setHighlightedBlockIndex(null);
        return true;
      }
      if (showWeakCompiler) {
        setShowWeakCompiler(false);
        return true;
      }
      if (searchQuery.trim()) {
        setSearchQuery('');
        return true;
      }
      if (selectedSubject) {
        setSelectedSubject(null);
        return true;
      }
      if (selectedCourse) {
        setSelectedCourse(null);
        return true;
      }
      return false;
    });
    return () => registerBackHandler(null);
  }, [
    registerBackHandler,
    showNavigator,
    appendTargetNote,
    selectedNote,
    viewMode,
    showWeakCompiler,
    searchQuery,
    selectedSubject,
    selectedCourse,
  ]);

  const parsedBlocks = useMemo<StudyNoteBlock[]>(() => {
    if (!selectedNote) return [];
    return StudyNoteParser.parseBlocks(selectedNote.rawContent, selectedNote.formatRule);
  }, [selectedNote]);

  const studyUnits = useMemo(
    () =>
      parsedBlocks.filter(
        (b): b is Extract<StudyNoteBlock, { kind: 'one_liner' | 'multi_liner_card' }> =>
          b.kind === 'one_liner' || b.kind === 'multi_liner_card'
      ),
    [parsedBlocks]
  );

  const handleToggleBookmark = (note: StudyNoteRecord, blockIndex: number) => {
    const cur = new Set(note.bookmarkedIndices || []);
    if (cur.has(blockIndex)) cur.delete(blockIndex);
    else cur.add(blockIndex);
    onSaveNote({
      ...note,
      bookmarkedIndices: Array.from(cur),
      updatedAt: Date.now(),
    });
  };

  const handleToggleMastered = (note: StudyNoteRecord, blockIndex: number) => {
    const cur = new Set(note.masteredIndices || []);
    if (cur.has(blockIndex)) cur.delete(blockIndex);
    else cur.add(blockIndex);
    onSaveNote({
      ...note,
      masteredIndices: Array.from(cur),
      updatedAt: Date.now(),
    });
  };

  const handleUpdateSrs = (note: StudyNoteRecord, nextStates: Record<number, FlashcardSrsState>) => {
    onSaveNote({
      ...note,
      srsStates: nextStates,
      updatedAt: Date.now(),
    });
  };

  const handleCreateCourse = (courseName: string) => {
    const trimmed = courseName.trim();
    if (!trimmed) return;
    const exists = Object.keys(registeredCoursesMap).some(
      (k) => k.toLowerCase() === trimmed.toLowerCase()
    );
    if (!exists) {
      onSaveCoursesMap({ ...registeredCoursesMap, [trimmed]: [] });
      onNotify?.(`Created course "${trimmed}"`);
    }
  };

  const handleAddSubjectToCourse = (courseName: string, subjectName: string) => {
    const cTrim = courseName.trim();
    const sTrim = subjectName.trim();
    if (!cTrim || !sTrim) return;
    const existingKey =
      Object.keys(registeredCoursesMap).find((k) => k.toLowerCase() === cTrim.toLowerCase()) || cTrim;
    const curSubs = registeredCoursesMap[existingKey] || [];
    if (!curSubs.some((s) => s.toLowerCase() === sTrim.toLowerCase())) {
      onSaveCoursesMap({
        ...registeredCoursesMap,
        [existingKey]: [...curSubs, sTrim],
      });
      onNotify?.(`Added subject "${sTrim}" to "${existingKey}"`);
    }
  };

  const handleRenameCourse = (oldCourse: string, newCourse: string) => {
    const oldTrim = oldCourse.trim();
    const newTrim = newCourse.trim();
    if (!oldTrim || !newTrim || oldTrim.toLowerCase() === newTrim.toLowerCase()) return;

    const nextMap: KnowledgeCoursesMap = {};
    const movedSubjects: string[] = [];
    for (const [k, subs] of Object.entries(registeredCoursesMap)) {
      if (k.toLowerCase() === oldTrim.toLowerCase()) {
        movedSubjects.push(...subs);
      } else {
        nextMap[k] = subs;
      }
    }
    const existingTarget = nextMap[newTrim] || [];
    nextMap[newTrim] = Array.from(new Set([...existingTarget, ...movedSubjects]));
    onSaveCoursesMap(nextMap);

    const updatedNotes = notes.map((n) =>
      ((n.course || '').trim() || DEFAULT_UNCATEGORIZED_COURSE).toLowerCase() === oldTrim.toLowerCase()
        ? { ...n, course: newTrim, updatedAt: Date.now() }
        : n
    );
    if (onSaveNotesBatch) {
      onSaveNotesBatch(updatedNotes, `Renamed course "${oldTrim}" to "${newTrim}"`);
    } else {
      updatedNotes.forEach((n) => onSaveNote(n));
    }
    if (selectedCourse?.toLowerCase() === oldTrim.toLowerCase()) {
      setSelectedCourse(newTrim);
    }
  };

  const handleRenameSubject = (courseName: string, oldSubject: string, newSubject: string) => {
    const cTrim = courseName.trim();
    const oldTrim = oldSubject.trim();
    const newTrim = newSubject.trim();
    if (!oldTrim || !newTrim || oldTrim.toLowerCase() === newTrim.toLowerCase()) return;

    const nextMap: KnowledgeCoursesMap = { ...registeredCoursesMap };
    const existingCourseKey = Object.keys(nextMap).find(
      (k) => k.toLowerCase() === cTrim.toLowerCase()
    );
    if (existingCourseKey) {
      const updatedSubs = (nextMap[existingCourseKey] || []).map((s) =>
        s.toLowerCase() === oldTrim.toLowerCase() ? newTrim : s
      );
      if (!updatedSubs.some((s) => s.toLowerCase() === newTrim.toLowerCase())) {
        updatedSubs.push(newTrim);
      }
      nextMap[existingCourseKey] = Array.from(new Set(updatedSubs));
      onSaveCoursesMap(nextMap);
    }

    const updatedNotes = notes.map((n) => {
      const noteCourse = (n.course || '').trim() || DEFAULT_UNCATEGORIZED_COURSE;
      const noteSub = (n.subject || '').trim() || 'General Studies';
      return noteCourse.toLowerCase() === cTrim.toLowerCase() &&
        noteSub.toLowerCase() === oldTrim.toLowerCase()
        ? { ...n, subject: newTrim, updatedAt: Date.now() }
        : n;
    });
    if (onSaveNotesBatch) {
      onSaveNotesBatch(updatedNotes, `Renamed subject "${oldTrim}" to "${newTrim}"`);
    } else {
      updatedNotes.forEach((n) => onSaveNote(n));
    }
    if (selectedSubject?.toLowerCase() === oldTrim.toLowerCase()) {
      setSelectedSubject(newTrim);
    }
  };

  const handleAssignNoteCourseSubject = (noteId: string, courseName: string, subjectName: string) => {
    const target = notes.find((n) => n.id === noteId);
    if (!target) return;
    const cleanCourse = courseName === DEFAULT_UNCATEGORIZED_COURSE ? '' : courseName.trim();
    const cleanSubject = subjectName.trim() || 'General Studies';
    if (cleanCourse) {
      handleAddSubjectToCourse(cleanCourse, cleanSubject);
    }
    onSaveNote({
      ...target,
      course: cleanCourse,
      subject: cleanSubject,
      updatedAt: Date.now(),
    });
    onNotify?.(`Moved "${target.title}" to ${cleanCourse || DEFAULT_UNCATEGORIZED_COURSE} → ${cleanSubject}`);
  };

  const handleCompileWeakAreas = (paper: QuestionPaper) => {
    const paperAttempts = attempts.filter((a) => a.paperId === paper.metadata.id);
    const latest = paperAttempts[0];
    const submittedAnswers = latest?.submittedAnswers || {};
    const compiled = StudyNoteParser.compileWeakAreasFromPaper(paper, submittedAnswers);
    const now = Date.now();
    const newNote: StudyNoteRecord = {
      id: `note-weak-${now}-${Math.random().toString(36).slice(2, 6)}`,
      title: compiled.title,
      course: selectedCourse && selectedCourse !== DEFAULT_UNCATEGORIZED_COURSE ? selectedCourse : '',
      subject: paper.metadata.subject || 'Weak-Area Review',
      sourceAi: latest ? 'Compiled from Latest Exam Attempt' : 'Compiled from Exam Paper Questions',
      rawContent: compiled.rawContent,
      folderId: paper.metadata.folderId || 'default',
      collectionType: 'WEAK_AREA_REVIEW',
      customLabel: '',
      linkedPaperId: paper.metadata.id,
      formatRule: {
        ...DEFAULT_KNOWLEDGE_RULE,
        collectionType: 'WEAK_AREA_REVIEW',
      },
      bookmarkedIndices: [],
      masteredIndices: [],
      srsStates: {},
      createdAt: now,
      updatedAt: now,
    };
    onSaveNote(newNote);
    setShowWeakCompiler(false);
    setSelectedNoteId(newNote.id);
    setViewMode('READER');
  };

  const handlePrintNote = (note: StudyNoteRecord) => {
    const html = StudyNoteParser.generatePrintableHtml(note);
    const popup = window.open('', '_blank', 'width=900,height=750');
    if (!popup) return;
    popup.document.open();
    popup.document.write(html);
    popup.document.close();
    popup.focus();
    setTimeout(() => popup.print(), 250);
  };

  const jumpToBlock = (blockIndex: number) => {
    setHighlightedBlockIndex(blockIndex);
    setTimeout(() => {
      const el = document.getElementById(`knowledge-block-${blockIndex}`);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 40);
  };

  // --- READER / DECK SUB-VIEW ---
  if (selectedNote) {
    const bookmarkedSet = new Set(selectedNote.bookmarkedIndices || []);
    const masteredSet = new Set(selectedNote.masteredIndices || []);
    const masteredUnitsCount = studyUnits.filter((u) => masteredSet.has(u.blockIndex)).length;
    const masteryPct = studyUnits.length > 0 ? Math.round((masteredUnitsCount / studyUnits.length) * 100) : 0;

    const visibleBlocks = parsedBlocks.filter((b) => {
      if (unitFilter === 'ALL') return true;
      if (b.kind === 'heading') return true;
      if (unitFilter === 'STARRED') return bookmarkedSet.has(b.blockIndex);
      if (unitFilter === 'UNMASTERED') {
        return (b.kind === 'one_liner' || b.kind === 'multi_liner_card') && !masteredSet.has(b.blockIndex);
      }
      return true;
    });

    const meta = KNOWLEDGE_COLLECTION_META[selectedNote.collectionType];
    const badgeLabel =
      selectedNote.collectionType === 'CUSTOM' && selectedNote.customLabel
        ? selectedNote.customLabel
        : meta.shortBadge;

    return (
      <div className="space-y-4" ref={readerTopRef}>
        {/* Top Action Bar */}
        <div
          className="p-4 rounded-xl border flex flex-col lg:flex-row lg:items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-start gap-3">
            <button
              type="button"
              onClick={() => {
                if (viewMode === 'FLASHCARDS') {
                  setViewMode('READER');
                } else {
                  setSelectedNoteId(null);
                  setRevealedIndices({});
                  setHighlightedBlockIndex(null);
                }
              }}
              className="flex items-center gap-1 px-3 py-2 rounded-lg border text-xs font-bold flex-shrink-0"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <ArrowLeft className="w-4 h-4" />
              {viewMode === 'FLASHCARDS' ? 'Reader' : 'Back'}
            </button>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-1.5">
                <span
                  className="px-2 py-0.5 rounded-md text-[0.625rem] font-black border"
                  style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                >
                  {badgeLabel}
                </span>
                <span className="text-[0.6875rem] font-semibold" style={{ color: colors.muted }}>
                  {(selectedNote.course || '').trim() || DEFAULT_UNCATEGORIZED_COURSE} → {selectedNote.subject}
                </span>
              </div>
              <h3 className="text-base sm:text-lg font-black mt-0.5 es-clamp-2" style={{ color: colors.inkBold }}>
                {selectedNote.title}
              </h3>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            <div
              className="flex rounded-lg border p-0.5"
              style={{ backgroundColor: colors.surface, borderColor: colors.border }}
            >
              <button
                type="button"
                onClick={() => setViewMode('READER')}
                className="px-3 py-1.5 rounded-md font-bold transition"
                style={
                  viewMode === 'READER'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { color: colors.ink }
                }
              >
                Study Reader
              </button>
              <button
                type="button"
                onClick={() => setViewMode('FLASHCARDS')}
                className="flex items-center gap-1 px-3 py-1.5 rounded-md font-bold transition"
                style={
                  viewMode === 'FLASHCARDS'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { color: colors.ink }
                }
              >
                <Layers className="w-3.5 h-3.5" />
                SM-2 Deck
              </button>
            </div>

            {viewMode === 'READER' && (
              <button
                type="button"
                onClick={() => setShowNavigator(true)}
                className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.teal, color: colors.tealDark }}
              >
                <Grid className="w-3.5 h-3.5" />
                Navigator
              </button>
            )}

            <button
              type="button"
              onClick={() => setAppendTargetNote(selectedNote)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }}
            >
              <FilePlus2 className="w-3.5 h-3.5" />
              + Append
            </button>

            <button
              type="button"
              onClick={() => onSendNoteToDictation(selectedNote.id)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.tealDark }}
            >
              <Mic className="w-3.5 h-3.5" />
              Dictation
            </button>

            <button
              type="button"
              onClick={() => handlePrintNote(selectedNote)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
            >
              <Printer className="w-3.5 h-3.5" />
              Print
            </button>

            <button
              type="button"
              onClick={() => onOpenEditor(selectedNote)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <Edit3 className="w-3.5 h-3.5" />
              Edit
            </button>
          </div>
        </div>

        {/* Mode Body */}
        {viewMode === 'FLASHCARDS' ? (
          <FlashcardDeckView
            note={selectedNote}
            blocks={parsedBlocks}
            onUpdateSrsStates={(nextStates) => handleUpdateSrs(selectedNote, nextStates)}
            onToggleBookmark={(idx) => handleToggleBookmark(selectedNote, idx)}
          />
        ) : (
          <div className="space-y-3.5">
            {/* Study-First Mastery & Recall Transition Banner */}
            {studyUnits.length > 0 && (
              <div
                className="p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                style={{ backgroundColor: colors.surface, borderColor: colors.border }}
              >
                <div className="flex-1 space-y-1.5">
                  <div className="flex items-center justify-between text-xs font-bold">
                    <span style={{ color: colors.inkBold }}>
                      Study Progress: {masteredUnitsCount} / {studyUnits.length} Mastered ({masteryPct}%)
                    </span>
                    <span className="text-[0.6875rem]" style={{ color: colors.muted }}>
                      Mark understood items ✓ before SM-2 recall
                    </span>
                  </div>
                  <div className="w-full h-2 rounded-full overflow-hidden" style={{ backgroundColor: colors.surfaceVariant }}>
                    <div
                      className="h-full transition-all"
                      style={{ width: `${masteryPct}%`, backgroundColor: colors.green }}
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setViewMode('FLASHCARDS')}
                  className="px-3.5 py-2 rounded-xl text-xs font-black flex items-center gap-1.5 flex-shrink-0"
                  style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                >
                  <Layers className="w-3.5 h-3.5" />
                  Start SM-2 Recall →
                </button>
              </div>
            )}

            {/* Quick Horizontal Jump Strip */}
            {studyUnits.length > 1 && (
              <div
                className="p-2.5 rounded-xl border flex items-center gap-2 overflow-x-auto"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <span className="text-[0.6875rem] font-black flex-shrink-0" style={{ color: colors.muted }}>
                  Jump:
                </span>
                {studyUnits.map((unit, idx) => {
                  const isMastered = masteredSet.has(unit.blockIndex);
                  const isStarred = bookmarkedSet.has(unit.blockIndex);
                  const isActive = highlightedBlockIndex === unit.blockIndex;
                  return (
                    <button
                      key={unit.blockIndex}
                      type="button"
                      onClick={() => jumpToBlock(unit.blockIndex)}
                      className="min-w-[34px] h-[30px] px-2 rounded-lg border text-[0.6875rem] font-black flex-shrink-0 transition"
                      style={{
                        backgroundColor: isActive
                          ? colors.teal
                          : isMastered
                          ? colors.greenLight
                          : colors.surface,
                        color: isActive
                          ? colors.onAccentText
                          : isMastered
                          ? colors.greenDark
                          : colors.inkBold,
                        borderColor: isActive ? colors.teal : isStarred ? colors.amber : colors.border,
                      }}
                    >
                      #{idx + 1}
                    </button>
                  );
                })}
              </div>
            )}

            {/* Active Recall Mask & Unit Filter Controls */}
            <div
              className="p-3 rounded-xl border flex flex-wrap items-center justify-between gap-2 text-xs"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="font-bold mr-1 flex items-center gap-1" style={{ color: colors.inkBold }}>
                  {maskMode === 'NONE' ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                  Mask:
                </span>
                {(
                  [
                    { id: 'NONE' as ReaderMaskMode, label: 'Show All' },
                    { id: 'HIDE_DETAIL' as ReaderMaskMode, label: 'Hide Detail / Answer' },
                    { id: 'HIDE_EXPLANATION' as ReaderMaskMode, label: 'Hide Takeaway / Sub-Points' },
                    { id: 'HIDE_LEAD' as ReaderMaskMode, label: 'Reverse (Hide Lead)' },
                  ]
                ).map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => {
                      setMaskMode(m.id);
                      setRevealedIndices({});
                    }}
                    className="px-2.5 py-1 rounded-lg border font-bold text-[0.6875rem]"
                    style={
                      maskMode === m.id
                        ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                        : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                    }
                  >
                    {m.label}
                  </button>
                ))}
              </div>

              <div className="flex flex-wrap items-center gap-1.5">
                {(
                  [
                    { id: 'ALL' as ReaderUnitFilter, label: 'All' },
                    { id: 'UNMASTERED' as ReaderUnitFilter, label: `Remaining (${Math.max(0, studyUnits.length - masteredUnitsCount)})` },
                    { id: 'STARRED' as ReaderUnitFilter, label: `Starred (${bookmarkedSet.size})` },
                  ]
                ).map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => setUnitFilter(f.id)}
                    className="px-2.5 py-1 rounded-lg border font-bold text-[0.6875rem]"
                    style={
                      unitFilter === f.id
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Rendered Knowledge Blocks */}
            <div className="space-y-3">
              {visibleBlocks.map((block) => {
                const isStarred = bookmarkedSet.has(block.blockIndex);
                const isMastered = masteredSet.has(block.blockIndex);
                const isHighlighted = highlightedBlockIndex === block.blockIndex;
                const isPeeked = Boolean(revealedIndices[block.blockIndex]);
                const effectiveMask = isPeeked ? 'NONE' : maskMode;

                const togglePeek = () =>
                  setRevealedIndices((prev) => ({
                    ...prev,
                    [block.blockIndex]: !prev[block.blockIndex],
                  }));

                if (block.kind === 'heading') {
                  return (
                    <div
                      key={block.blockIndex}
                      id={`knowledge-block-${block.blockIndex}`}
                      className="pt-3 pb-1 border-b-2"
                      style={{ borderColor: colors.teal }}
                    >
                      <h4
                        className="font-black"
                        style={{
                          color: colors.inkBold,
                          fontSize: scalePx(block.level <= 2 ? 16 : 14),
                        }}
                      >
                        <LatexText text={block.text} />
                      </h4>
                    </div>
                  );
                }

                if (block.kind === 'table') {
                  return (
                    <div key={block.blockIndex} id={`knowledge-block-${block.blockIndex}`}>
                      <MarkdownTableCard table={{ headers: block.headers, rows: block.rows }} />
                    </div>
                  );
                }

                if (block.kind === 'callout') {
                  return (
                    <div
                      key={block.blockIndex}
                      id={`knowledge-block-${block.blockIndex}`}
                      className="p-3.5 rounded-xl border-l-4 text-xs leading-relaxed"
                      style={{
                        backgroundColor: colors.blueLight,
                        borderColor: colors.teal,
                        color: colors.inkBold,
                      }}
                    >
                      <strong className="mr-1.5">{block.label}:</strong>
                      <LatexText text={block.body} />
                    </div>
                  );
                }

                if (block.kind === 'paragraph') {
                  return (
                    <div
                      key={block.blockIndex}
                      id={`knowledge-block-${block.blockIndex}`}
                      className="p-3 rounded-xl border text-sm leading-relaxed"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.borderLight, color: colors.ink }}
                    >
                      <LatexText text={block.text} />
                    </div>
                  );
                }

                if (block.kind === 'one_liner') {
                  const hideLead = effectiveMask === 'HIDE_LEAD' && Boolean(block.leadTerm);
                  const hideDetail = effectiveMask === 'HIDE_DETAIL' && block.hasRecallTarget;
                  return (
                    <div
                      key={block.blockIndex}
                      id={`knowledge-block-${block.blockIndex}`}
                      className="p-3.5 rounded-xl border flex items-start justify-between gap-3 transition"
                      style={{
                        backgroundColor: isMastered
                          ? colors.greenLight
                          : isStarred
                          ? colors.amberLight
                          : colors.surfaceVariant,
                        borderColor: isHighlighted
                          ? colors.teal
                          : isMastered
                          ? colors.green
                          : isStarred
                          ? colors.amber
                          : colors.border,
                        borderWidth: isHighlighted ? '2px' : '1px',
                      }}
                    >
                      <div className="flex-1 text-sm leading-relaxed" style={{ color: colors.ink }}>
                        <span className="font-black mr-2" style={{ color: colors.teal }}>
                          {block.displayIndex}.
                        </span>
                        {block.leadTerm && (
                          <>
                            <span className="font-bold" style={{ color: colors.inkBold }}>
                              {hideLead ? '[ • • • Hidden Term • • • ]' : <LatexText text={block.leadTerm} />}
                            </span>
                            <span className="mx-1.5" style={{ color: colors.muted }}>
                              →
                            </span>
                          </>
                        )}
                        {hideDetail ? (
                          <button
                            type="button"
                            onClick={togglePeek}
                            className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md border text-xs font-bold"
                            style={{ backgroundColor: colors.surface, borderColor: colors.teal, color: colors.tealDark }}
                          >
                            <Eye className="w-3 h-3" />
                            Tap to Reveal Answer
                          </button>
                        ) : (
                          <span>
                            <LatexText text={block.detail} />
                          </span>
                        )}
                      </div>

                      <div className="flex items-center gap-1 flex-shrink-0">
                        <button
                          type="button"
                          onClick={() => handleToggleMastered(selectedNote, block.blockIndex)}
                          className="p-1.5 rounded-lg border"
                          style={{
                            backgroundColor: isMastered ? colors.green : colors.surface,
                            borderColor: isMastered ? colors.green : colors.border,
                            color: isMastered ? '#ffffff' : colors.muted,
                          }}
                          title={isMastered ? 'Marked Mastered' : 'Mark Understood / Mastered'}
                        >
                          <Check className="w-3.5 h-3.5" />
                        </button>
                        {maskMode !== 'NONE' && (
                          <button
                            type="button"
                            onClick={togglePeek}
                            className="p-1.5 rounded-lg border text-[0.6875rem]"
                            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                            title="Toggle Peek"
                          >
                            {isPeeked ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleToggleBookmark(selectedNote, block.blockIndex)}
                          className="p-1.5 rounded-lg border"
                          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
                          title="Star Item"
                        >
                          <Star
                            className="w-3.5 h-3.5"
                            style={{
                              color: isStarred ? colors.amber : colors.muted,
                              fill: isStarred ? colors.amber : 'none',
                            }}
                          />
                        </button>
                      </div>
                    </div>
                  );
                }

                // Multi-liner / Q&A / Question remediation card
                const hideDetail = effectiveMask === 'HIDE_DETAIL';
                const hideExpl = effectiveMask === 'HIDE_EXPLANATION' || hideDetail;
                const hideLead = effectiveMask === 'HIDE_LEAD';

                return (
                  <div
                    key={block.blockIndex}
                    id={`knowledge-block-${block.blockIndex}`}
                    className="p-4 rounded-xl border space-y-2.5 transition"
                    style={{
                      backgroundColor: isMastered
                        ? colors.greenLight
                        : isStarred
                        ? colors.amberLight
                        : colors.surface,
                      borderColor: isHighlighted
                        ? colors.teal
                        : isMastered
                        ? colors.green
                        : isStarred
                        ? colors.amber
                        : colors.border,
                      borderWidth: isHighlighted ? '2px' : '1px',
                    }}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1">
                        <div className="flex flex-wrap items-center gap-1.5 mb-1">
                          <span className="text-xs font-black" style={{ color: colors.teal }}>
                            {block.displayIndex}
                          </span>
                          {(block.badge || block.questionType) && (
                            <span
                              className="px-2 py-0.5 rounded text-[0.625rem] font-bold border"
                              style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.border }}
                            >
                              {block.badge || block.questionType}
                            </span>
                          )}
                          {block.statusTag && (
                            <span
                              className="px-2 py-0.5 rounded text-[0.625rem] font-bold"
                              style={{ backgroundColor: colors.redLight, color: colors.red }}
                            >
                              {block.statusTag}
                            </span>
                          )}
                        </div>
                        <div className="text-sm font-bold leading-snug" style={{ color: colors.inkBold }}>
                          {hideLead ? '[ • • • Recall Title/Concept • • • ]' : <LatexText text={block.title} />}
                        </div>
                      </div>

                      <div className="flex items-center gap-1 flex-shrink-0">
                        <button
                          type="button"
                          onClick={() => handleToggleMastered(selectedNote, block.blockIndex)}
                          className="p-1.5 rounded-lg border"
                          style={{
                            backgroundColor: isMastered ? colors.green : colors.surfaceVariant,
                            borderColor: isMastered ? colors.green : colors.border,
                            color: isMastered ? '#ffffff' : colors.muted,
                          }}
                          title={isMastered ? 'Marked Mastered' : 'Mark Understood / Mastered'}
                        >
                          <CheckCircle2 className="w-3.5 h-3.5" />
                        </button>
                        {maskMode !== 'NONE' && (
                          <button
                            type="button"
                            onClick={togglePeek}
                            className="p-1.5 rounded-lg border"
                            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
                          >
                            {isPeeked ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleToggleBookmark(selectedNote, block.blockIndex)}
                          className="p-1.5 rounded-lg border"
                          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                        >
                          <Star
                            className="w-3.5 h-3.5"
                            style={{
                              color: isStarred ? colors.amber : colors.muted,
                              fill: isStarred ? colors.amber : 'none',
                            }}
                          />
                        </button>
                      </div>
                    </div>

                    {block.optionsList.length > 0 && (
                      <div className="space-y-1 text-xs pl-2" style={{ color: colors.ink }}>
                        {block.optionsList.map((o) => (
                          <div key={o.label}>
                            <strong>({o.label})</strong> <LatexText text={o.text} />
                          </div>
                        ))}
                      </div>
                    )}

                    {hideDetail ? (
                      <button
                        type="button"
                        onClick={togglePeek}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold"
                        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal, color: colors.tealDark }}
                      >
                        <Eye className="w-3.5 h-3.5" />
                        Tap to Reveal Card Body &amp; Answer
                      </button>
                    ) : (
                      <>
                        {block.paragraphs.map((p, pIdx) => (
                          <div key={pIdx} className="text-sm leading-relaxed" style={{ color: colors.ink }}>
                            <LatexText text={p} />
                          </div>
                        ))}

                        {block.correctAnswer && (
                          <div
                            className="px-3 py-1.5 rounded-lg border text-xs font-bold"
                            style={{ backgroundColor: colors.greenLight, borderColor: colors.green, color: colors.greenDark }}
                          >
                            Correct Answer: <LatexText text={block.correctAnswer} />
                          </div>
                        )}

                        {!hideExpl ? (
                          <>
                            {block.formula && (
                              <div
                                className="px-3 py-2 rounded-lg border font-mono text-xs"
                                style={{ backgroundColor: colors.blueLight, borderColor: colors.border, color: colors.inkBold }}
                              >
                                <strong className="mr-1">Formula:</strong>
                                <LatexText text={block.formula} />
                              </div>
                            )}
                            {block.keyTakeaway && (
                              <div
                                className="px-3 py-2 rounded-lg border text-xs"
                                style={{ backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.inkBold }}
                              >
                                <strong>Key Takeaway:</strong> <LatexText text={block.keyTakeaway} />
                              </div>
                            )}
                            {block.nestedBullets.length > 0 && (
                              <ul className="space-y-1 pl-4 list-disc text-xs" style={{ color: colors.ink }}>
                                {block.nestedBullets.map((nb, nIdx) => (
                                  <li key={nIdx} style={{ marginLeft: `${(nb.level - 1) * 10}px` }}>
                                    <LatexText text={nb.text} />
                                  </li>
                                ))}
                              </ul>
                            )}
                          </>
                        ) : (
                          (block.formula || block.keyTakeaway || block.nestedBullets.length > 0) && (
                            <button
                              type="button"
                              onClick={togglePeek}
                              className="text-xs font-bold underline"
                              style={{ color: colors.tealDark }}
                            >
                              Show Formula &amp; Key Takeaway
                            </button>
                          )
                        )}
                      </>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <KnowledgeNavigatorSheet
          isOpen={showNavigator}
          noteTitle={selectedNote.title}
          badgeLabel={badgeLabel}
          allBlocks={parsedBlocks}
          bookmarkedSet={bookmarkedSet}
          masteredSet={masteredSet}
          srsStates={selectedNote.srsStates || {}}
          currentBlockIndex={highlightedBlockIndex}
          onJumpToBlock={jumpToBlock}
          onScrollToTop={() => readerTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
          onClose={() => setShowNavigator(false)}
        />

        <AppendKnowledgeModal
          isOpen={Boolean(appendTargetNote)}
          note={appendTargetNote}
          onApplyAppend={(updated, addedCount, skippedCount) => {
            onSaveNote(updated);
            setAppendTargetNote(null);
            onNotify?.(
              `Appended +${addedCount} item(s)${skippedCount > 0 ? ` (${skippedCount} duplicates skipped)` : ''}`
            );
          }}
          onClose={() => setAppendTargetNote(null)}
        />
      </div>
    );
  }

  // --- COURSE -> SUBJECT -> NOTES LIBRARY VIEW ---
  return (
    <div className="space-y-5">
      {/* Header */}
      <div
        className="p-4 sm:p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        style={{ background: colors.heroGradient, borderColor: colors.border, boxShadow: colors.cardShadow }}
      >
        <div className="min-w-0">
          <h3 className="text-base font-black flex items-center gap-2 tracking-tight" style={{ color: colors.inkBold }}>
            <BookOpen className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            <span className="es-clamp-2">Knowledge Studio &amp; SM-2 Spaced Repetition ({notes.length})</span>
          </h3>
          <p className="text-xs mt-1 es-scroll-x" style={{ color: colors.muted }}>
            Organize Courses → Subjects → Study Sets, master concepts in Reader mode, then practice with SM-2 Flashcards.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setShowWeakCompiler((v) => !v)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg border text-xs font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.amber, color: colors.amber }}
          >
            <FileWarning className="w-4 h-4" />
            Compile Weak Areas
          </button>

          <button
            type="button"
            onClick={() =>
              onOpenEditor(null, {
                course:
                  selectedCourse && selectedCourse !== DEFAULT_UNCATEGORIZED_COURSE
                    ? selectedCourse
                    : undefined,
                subject: selectedSubject || undefined,
              })
            }
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold shadow-sm"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <Plus className="w-4 h-4" />
            New Study Note
          </button>
        </div>
      </div>

      {/* Compile Weak Areas Picker */}
      {showWeakCompiler && (
        <div
          className="p-4 rounded-xl border space-y-3"
          style={{ backgroundColor: colors.amberLight, borderColor: colors.amber }}
        >
          <div className="flex items-center justify-between">
            <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
              Select an Exam Paper to compile missed/wrong/skipped questions into a Weak-Area Study Log:
            </div>
            <button
              type="button"
              onClick={() => setShowWeakCompiler(false)}
              className="text-xs font-bold underline"
              style={{ color: colors.ink }}
            >
              Close
            </button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {papers.map((p) => {
              const latest = attempts.find((a) => a.paperId === p.metadata.id);
              return (
                <button
                  key={p.metadata.id}
                  type="button"
                  onClick={() => handleCompileWeakAreas(p)}
                  className="p-3 rounded-xl border text-left transition"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border }}
                >
                  <div className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                    {p.metadata.title}
                  </div>
                  <div className="text-[0.6875rem] mt-0.5" style={{ color: colors.muted }}>
                    {p.questions.length} Qs • {latest ? `Latest score: ${latest.score.toFixed(1)}` : 'Full paper review'}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Course -> Subject -> Notes Hierarchy Browser */}
      <KnowledgeCourseBrowser
        notes={notes}
        folders={folders}
        registeredCoursesMap={registeredCoursesMap}
        selectedCourse={selectedCourse}
        selectedSubject={selectedSubject}
        searchQuery={searchQuery}
        filterType={filterType}
        onSelectCourse={setSelectedCourse}
        onSelectSubject={setSelectedSubject}
        onSearchQueryChange={setSearchQuery}
        onFilterTypeChange={setFilterType}
        onCreateCourse={handleCreateCourse}
        onAddSubjectToCourse={handleAddSubjectToCourse}
        onRenameCourse={handleRenameCourse}
        onRenameSubject={handleRenameSubject}
        onAssignNoteCourseSubject={handleAssignNoteCourseSubject}
        onOpenStudy={(note) => {
          setSelectedNoteId(note.id);
          setViewMode('READER');
        }}
        onOpenSm2={(note) => {
          setSelectedNoteId(note.id);
          setViewMode('FLASHCARDS');
        }}
        onOpenAppend={(note) => setAppendTargetNote(note)}
        onOpenEditor={(note, prefill) => onOpenEditor(note, prefill)}
        onSendNoteToDictation={onSendNoteToDictation}
        onDeleteNote={onDeleteNote}
      />

      <AppendKnowledgeModal
        isOpen={Boolean(appendTargetNote)}
        note={appendTargetNote}
        onApplyAppend={(updated, addedCount, skippedCount) => {
          onSaveNote(updated);
          setAppendTargetNote(null);
          onNotify?.(
            `Appended +${addedCount} item(s)${skippedCount > 0 ? ` (${skippedCount} duplicates skipped)` : ''}`
          );
        }}
        onClose={() => setAppendTargetNote(null)}
      />
    </div>
  );
};

export default KnowledgeStudioTab;
