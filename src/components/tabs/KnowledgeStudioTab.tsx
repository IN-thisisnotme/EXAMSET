import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  BookOpen,
  Edit3,
  Eye,
  EyeOff,
  FileWarning,
  Layers,
  Mic,
  Plus,
  Printer,
  Search,
  Star,
  Trash2,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import { AttemptRecord, FolderItem, QuestionPaper } from '../../types/exam';
import {
  DEFAULT_KNOWLEDGE_RULE,
  FlashcardSrsState,
  KNOWLEDGE_COLLECTION_META,
  KnowledgeCollectionType,
  StudyNoteBlock,
  StudyNoteRecord,
} from '../../types/phase4';
import { SpacedRepetitionEngine } from '../../utils/spacedRepetitionEngine';
import { StudyNoteParser } from '../../utils/studyNoteParser';
import { FlashcardDeckView } from '../FlashcardDeckView';
import { LatexText } from '../LatexText';
import { MarkdownTableCard } from '../MarkdownTableCard';

type ReaderMaskMode =
  | 'NONE'
  | 'HIDE_DETAIL'
  | 'HIDE_EXPLANATION'
  | 'HIDE_LEAD'
  | 'CLUE_TO_TARGET';

interface KnowledgeStudioTabProps {
  notes: StudyNoteRecord[];
  papers: QuestionPaper[];
  attempts: AttemptRecord[];
  folders: FolderItem[];
  onSaveNote: (note: StudyNoteRecord) => void;
  onDeleteNote: (noteId: string) => void;
  onOpenEditor: (note?: StudyNoteRecord | null) => void;
  onSendNoteToDictation: (noteId: string) => void;
}

export const KnowledgeStudioTab: React.FC<KnowledgeStudioTabProps> = ({
  notes,
  papers,
  attempts,
  onSaveNote,
  onDeleteNote,
  onOpenEditor,
  onSendNoteToDictation,
}) => {
  const { colors, scalePx } = useTheme();
  const [selectedNoteId, setSelectedNoteId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'READER' | 'FLASHCARDS'>('READER');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterType, setFilterType] = useState<KnowledgeCollectionType | 'ALL'>('ALL');
  const [maskMode, setMaskMode] = useState<ReaderMaskMode>('NONE');
  const [revealedIndices, setRevealedIndices] = useState<Record<number, boolean>>({});
  const [bookmarkedOnly, setBookmarkedOnly] = useState<boolean>(false);
  const [showWeakCompiler, setShowWeakCompiler] = useState<boolean>(false);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

  const selectedNote = useMemo(
    () => notes.find((n) => n.id === selectedNoteId) || null,
    [notes, selectedNoteId]
  );

  const parsedBlocks = useMemo<StudyNoteBlock[]>(() => {
    if (!selectedNote) return [];
    return StudyNoteParser.parseBlocks(selectedNote.rawContent, selectedNote.formatRule);
  }, [selectedNote]);

  const filteredNotes = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return notes
      .filter((n) => filterType === 'ALL' || n.collectionType === filterType)
      .filter(
        (n) =>
          !q ||
          n.title.toLowerCase().includes(q) ||
          n.subject.toLowerCase().includes(q) ||
          n.rawContent.toLowerCase().includes(q)
      )
      .sort((a, b) => b.updatedAt - a.updatedAt);
  }, [notes, filterType, searchQuery]);

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

  const handleUpdateSrs = (note: StudyNoteRecord, nextStates: Record<number, FlashcardSrsState>) => {
    onSaveNote({
      ...note,
      srsStates: nextStates,
      updatedAt: Date.now(),
    });
  };

  const handleCompileWeakAreas = (paper: QuestionPaper) => {
    const paperAttempts = attempts.filter((a) => a.paperId === paper.metadata.id);
    const latest = paperAttempts[0];
    const submittedAnswers = latest?.answers || {};
    const compiled = StudyNoteParser.compileWeakAreasFromPaper(paper, submittedAnswers);
    const now = Date.now();
    const newNote: StudyNoteRecord = {
      id: `note-weak-${now}-${Math.random().toString(36).slice(2, 6)}`,
      title: compiled.title,
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

  // --- READER / DECK SUB-VIEW ---
  if (selectedNote) {
    const bookmarkedSet = new Set(selectedNote.bookmarkedIndices || []);
    const visibleBlocks = bookmarkedOnly
      ? parsedBlocks.filter((b) => b.kind === 'heading' || bookmarkedSet.has(b.blockIndex))
      : parsedBlocks;

    const meta = KNOWLEDGE_COLLECTION_META[selectedNote.collectionType];

    return (
      <div className="space-y-5">
        {/* Top Action Bar */}
        <div
          className="p-4 rounded-xl border flex flex-col lg:flex-row lg:items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-start gap-3">
            <button
              type="button"
              onClick={() => {
                setSelectedNoteId(null);
                setRevealedIndices({});
              }}
              className="flex items-center gap-1 px-3 py-2 rounded-lg border text-xs font-bold flex-shrink-0"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <ArrowLeft className="w-4 h-4" />
              Library
            </button>
            <div>
              <div className="flex flex-wrap items-center gap-1.5">
                <span
                  className="px-2 py-0.5 rounded-md text-[10px] font-black border"
                  style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                >
                  {selectedNote.collectionType === 'CUSTOM' && selectedNote.customLabel
                    ? selectedNote.customLabel
                    : meta.shortBadge}
                </span>
                <span className="text-[11px] font-semibold" style={{ color: colors.muted }}>
                  {selectedNote.subject}
                </span>
              </div>
              <h3 className="text-base sm:text-lg font-black mt-0.5" style={{ color: colors.inkBold }}>
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
                Notes Reader
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
              Print HTML
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
          <div className="space-y-4">
            {/* Active Recall Mask Controls */}
            <div
              className="p-3 rounded-xl border flex flex-wrap items-center justify-between gap-2 text-xs"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="flex flex-wrap items-center gap-1.5">
                <span className="font-bold mr-1 flex items-center gap-1" style={{ color: colors.inkBold }}>
                  {maskMode === 'NONE' ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
                  Active Recall Mask:
                </span>
                {(
                  [
                    { id: 'NONE' as ReaderMaskMode, label: 'Show All' },
                    { id: 'HIDE_DETAIL' as ReaderMaskMode, label: 'Hide Detail / Answer' },
                    { id: 'HIDE_EXPLANATION' as ReaderMaskMode, label: 'Hide Takeaway / Sub-Points' },
                    { id: 'HIDE_LEAD' as ReaderMaskMode, label: 'Reverse (Hide Lead Term)' },
                  ]
                ).map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => {
                      setMaskMode(m.id);
                      setRevealedIndices({});
                    }}
                    className="px-2.5 py-1 rounded-lg border font-bold text-[11px]"
                    style={
                      maskMode === m.id
                        ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                        : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                    }
                  >
                    {m.label}
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={() => setBookmarkedOnly((v) => !v)}
                className="flex items-center gap-1 px-2.5 py-1 rounded-lg border font-bold text-[11px]"
                style={
                  bookmarkedOnly
                    ? { backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.amber }
                    : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                }
              >
                <Star className="w-3.5 h-3.5" />
                Starred Only ({bookmarkedSet.size})
              </button>
            </div>

            {/* Rendered Knowledge Blocks */}
            <div className="space-y-3">
              {visibleBlocks.map((block) => {
                const isStarred = bookmarkedSet.has(block.blockIndex);
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
                    <MarkdownTableCard
                      key={block.blockIndex}
                      table={{ headers: block.headers, rows: block.rows }}
                    />
                  );
                }

                if (block.kind === 'callout') {
                  return (
                    <div
                      key={block.blockIndex}
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
                      className="p-3.5 rounded-xl border flex items-start justify-between gap-3 transition"
                      style={{
                        backgroundColor: isStarred ? colors.amberLight : colors.surfaceVariant,
                        borderColor: isStarred ? colors.amber : colors.border,
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

                      <div className="flex items-center gap-1">
                        {maskMode !== 'NONE' && (
                          <button
                            type="button"
                            onClick={togglePeek}
                            className="p-1.5 rounded-lg border text-[11px]"
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
                    className="p-4 rounded-xl border space-y-2.5"
                    style={{
                      backgroundColor: isStarred ? colors.amberLight : colors.surface,
                      borderColor: isStarred ? colors.amber : colors.border,
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
                              className="px-2 py-0.5 rounded text-[10px] font-bold border"
                              style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.border }}
                            >
                              {block.badge || block.questionType}
                            </span>
                          )}
                          {block.statusTag && (
                            <span
                              className="px-2 py-0.5 rounded text-[10px] font-bold"
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

                      <div className="flex items-center gap-1">
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
      </div>
    );
  }

  // --- LIBRARY GRID VIEW ---
  return (
    <div className="space-y-5">
      {/* Header */}
      <div
        className="p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
            <BookOpen className="w-5 h-5" style={{ color: colors.teal }} />
            Knowledge Studio &amp; SM-2 Spaced Repetition ({notes.length})
          </h3>
          <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
            Organize structured study notes, Weak-Area Logs, Formulas, and active-recall SM-2 flashcard decks.
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
            onClick={() => onOpenEditor(null)}
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
                  <div className="text-[11px] mt-0.5" style={{ color: colors.muted }}>
                    {p.questions.length} Qs • {latest ? `Latest score: ${latest.score.toFixed(1)}` : 'Full paper review'}
                  </div>
                </button>
              );
            })}
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
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search study notes, formulas, topics..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border text-xs"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
          />
        </div>

        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as KnowledgeCollectionType | 'ALL')}
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

      {/* Cards Grid */}
      {filteredNotes.length === 0 ? (
        <div
          className="p-10 rounded-xl border text-center space-y-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <BookOpen className="w-9 h-9 mx-auto" style={{ color: colors.muted }} />
          <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
            No matching study notes found.
          </p>
          <button
            type="button"
            onClick={() => onOpenEditor(null)}
            className="px-4 py-2 rounded-lg text-xs font-bold"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Create Study Note
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredNotes.map((note) => {
            const blocks = StudyNoteParser.parseBlocks(note.rawContent, note.formatRule);
            const cards = blocks.filter(
              (b) => b.kind === 'one_liner' || b.kind === 'multi_liner_card'
            );
            const srsStr: Record<string, FlashcardSrsState | undefined> = {};
            Object.entries(note.srsStates || {}).forEach(([k, v]) => {
              srsStr[String(k)] = v;
            });
            const srsStats = SpacedRepetitionEngine.computeDeckStats(cards, (c) => c.blockIndex, srsStr);
            const meta = KNOWLEDGE_COLLECTION_META[note.collectionType];

            return (
              <div
                key={note.id}
                className="rounded-xl border p-4 flex flex-col justify-between gap-3 transition"
                style={{ backgroundColor: colors.surface, borderColor: colors.border }}
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className="px-2 py-0.5 rounded-md text-[10px] font-black border"
                      style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                    >
                      {note.collectionType === 'CUSTOM' && note.customLabel ? note.customLabel : meta.shortBadge}
                    </span>
                    <span className="text-[11px] font-semibold truncate" style={{ color: colors.muted }}>
                      {note.subject}
                    </span>
                  </div>

                  <h4 className="text-sm font-black line-clamp-2" style={{ color: colors.inkBold }}>
                    {note.title}
                  </h4>

                  <div className="flex flex-wrap gap-1.5 text-[10px] font-bold">
                    <span
                      className="px-2 py-0.5 rounded"
                      style={{ backgroundColor: colors.surfaceVariant, color: colors.ink }}
                    >
                      {cards.length} Recall Items
                    </span>
                    {srsStats.dueCount + srsStats.newCount > 0 && (
                      <span
                        className="px-2 py-0.5 rounded"
                        style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}
                      >
                        {srsStats.dueCount + srsStats.newCount} Due/New
                      </span>
                    )}
                    {srsStats.masteredCount > 0 && (
                      <span
                        className="px-2 py-0.5 rounded"
                        style={{ backgroundColor: colors.greenLight, color: colors.greenDark }}
                      >
                        {srsStats.masteredCount} Mastered
                      </span>
                    )}
                  </div>
                </div>

                <div
                  className="pt-3 border-t flex items-center justify-between gap-1.5 text-xs"
                  style={{ borderColor: colors.borderLight }}
                >
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedNoteId(note.id);
                        setViewMode('READER');
                      }}
                      className="px-2.5 py-1.5 rounded-lg font-bold"
                      style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                    >
                      Read
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedNoteId(note.id);
                        setViewMode('FLASHCARDS');
                      }}
                      className="px-2.5 py-1.5 rounded-lg border font-bold"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.tealDark }}
                    >
                      SM-2
                    </button>
                  </div>

                  <div className="flex items-center gap-1">
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
                        className="px-2 py-1 rounded-lg font-bold text-white text-[10px]"
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
          })}
        </div>
      )}
    </div>
  );
};

export default KnowledgeStudioTab;
