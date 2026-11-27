import React, { useEffect, useMemo, useState } from 'react';
import {
  ClipboardPaste,
  Edit3,
  Eye,
  FileText,
  RotateCcw,
  Save,
  Sparkles,
  Wand2,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import { FolderItem, QuestionPaper } from '../types/exam';
import { buildUserFolderSuggestions } from '../utils/folderPathHelper';
import {
  DEFAULT_KNOWLEDGE_RULE,
  KNOWLEDGE_COLLECTION_META,
  KnowledgeCollectionType,
  KnowledgeCoursesMap,
  KnowledgeDepth,
  KnowledgeFormatRule,
  KnowledgeLayerOrder,
  KnowledgeTransformAction,
  StudyNoteRecord,
} from '../types/phase4';
import { StudyNoteParser } from '../utils/studyNoteParser';
import { LatexText } from './LatexText';
import { MarkdownTableCard } from './MarkdownTableCard';

interface StudyNoteEditorModalProps {
  isOpen: boolean;
  initialNote?: StudyNoteRecord | null;
  prefill?: {
    collectionType?: KnowledgeCollectionType;
    course?: string;
    subject?: string;
    topic?: string;
  } | null;
  papers: QuestionPaper[];
  folders: FolderItem[];
  registeredCoursesMap?: KnowledgeCoursesMap;
  existingNotes?: StudyNoteRecord[];
  onSaveNote: (note: StudyNoteRecord) => void;
  onClose: () => void;
  onRegisterCloseRequest?: (fn: (() => void) | null) => void;
}

export const StudyNoteEditorModal: React.FC<StudyNoteEditorModalProps> = ({
  isOpen,
  initialNote,
  prefill,
  papers,
  folders,
  registeredCoursesMap = {},
  existingNotes = [],
  onSaveNote,
  onClose,
  onRegisterCloseRequest,
}) => {
  const { colors } = useTheme();
  const [title, setTitle] = useState<string>('');
  const [course, setCourse] = useState<string>('');
  const [subject, setSubject] = useState<string>('');
  const [sourceAi, setSourceAi] = useState<string>('Custom / AI Paste');
  const [folderId, setFolderId] = useState<string>('default');
  const [collectionType, setCollectionType] = useState<KnowledgeCollectionType>('MULTI_LINER');
  const [customLabel, setCustomLabel] = useState<string>('');
  const [linkedPaperId, setLinkedPaperId] = useState<string>('');
  const [formatRule, setFormatRule] = useState<KnowledgeFormatRule>(DEFAULT_KNOWLEDGE_RULE);
  const [rawContent, setRawContent] = useState<string>('');
  const [undoStack, setUndoStack] = useState<string[]>([]);
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [editorMode, setEditorMode] = useState<'WRITE' | 'PREVIEW'>('WRITE');
  const [appendStatusMsg, setAppendStatusMsg] = useState<string>('');
  const [showInlineAppendBox, setShowInlineAppendBox] = useState<boolean>(false);
  const [inlineAppendDraft, setInlineAppendDraft] = useState<string>('');
  const [confirmDiscard, setConfirmDiscard] = useState<boolean>(false);
  const [initialSnapshot, setInitialSnapshot] = useState<string>('');

  useEffect(() => {
    if (!isOpen) return;
    let initTitle = '';
    let initCourse = '';
    let initSubject = '';
    let initSource = 'AI / Study Import';
    let initFolder = 'default';
    let initType: KnowledgeCollectionType = 'MULTI_LINER';
    let initCustom = '';
    let initPaper = '';
    let initRule: KnowledgeFormatRule = DEFAULT_KNOWLEDGE_RULE;
    let initRaw = '';

    if (initialNote) {
      initTitle = initialNote.title;
      initCourse = (initialNote.course || '').trim();
      initSubject = initialNote.subject;
      initSource = initialNote.sourceAi || 'Custom';
      initFolder = initialNote.folderId || 'default';
      initType = initialNote.collectionType;
      initCustom = initialNote.customLabel || '';
      initPaper = initialNote.linkedPaperId || '';
      initRule = initialNote.formatRule || {
        ...DEFAULT_KNOWLEDGE_RULE,
        collectionType: initialNote.collectionType,
      };
      initRaw = initialNote.rawContent;
    } else {
      initType = prefill?.collectionType || 'MULTI_LINER';
      initTitle = prefill?.topic ? `${prefill.topic} Notes` : '';
      initCourse = (prefill?.course || '').trim();
      initSubject = prefill?.subject || '';
      initRule = {
        ...DEFAULT_KNOWLEDGE_RULE,
        collectionType: initType,
        layerOrder: initType === 'FLASHCARDS' ? 'QA_SEPARATE_LINES' : 'AUTO_DETECT',
      };
    }

    setTitle(initTitle);
    setCourse(initCourse);
    setSubject(initSubject);
    setSourceAi(initSource);
    setFolderId(initFolder);
    setCollectionType(initType);
    setCustomLabel(initCustom);
    setLinkedPaperId(initPaper);
    setFormatRule(initRule);
    setRawContent(initRaw);
    setUndoStack([]);
    setErrorMsg('');
    setEditorMode('WRITE');
    setAppendStatusMsg('');
    setShowInlineAppendBox(false);
    setInlineAppendDraft('');
    setConfirmDiscard(false);
    setInitialSnapshot(
      JSON.stringify({
        title: initTitle,
        course: initCourse,
        subject: initSubject,
        rawContent: initRaw,
        collectionType: initType,
      })
    );
  }, [isOpen, initialNote, prefill]);

  const syncedRule = useMemo<KnowledgeFormatRule>(
    () => ({
      ...formatRule,
      collectionType,
      customCollectionLabel: customLabel,
    }),
    [formatRule, collectionType, customLabel]
  );

  const parsedBlocks = useMemo(
    () => (rawContent.trim() ? StudyNoteParser.parseBlocks(rawContent, syncedRule) : []),
    [rawContent, syncedRule]
  );

  const parsedCount = useMemo(
    () => parsedBlocks.filter((b) => b.kind === 'one_liner' || b.kind === 'multi_liner_card').length,
    [parsedBlocks]
  );

  const sourceHint = useMemo(
    () => (rawContent.trim() ? StudyNoteParser.detectSourceHint(rawContent) : ''),
    [rawContent]
  );

  const existingCoursesList = useMemo(() => {
    const set = new Set<string>();
    for (const c of Object.keys(registeredCoursesMap || {})) {
      if (c.trim()) set.add(c.trim());
    }
    for (const n of existingNotes) {
      if ((n.course || '').trim()) set.add((n.course || '').trim());
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [registeredCoursesMap, existingNotes]);

  const subjectSuggestionsForCourse = useMemo(() => {
    const set = new Set<string>();
    const cTrim = course.trim().toLowerCase();
    for (const [c, subs] of Object.entries(registeredCoursesMap || {})) {
      if (!cTrim || c.trim().toLowerCase() === cTrim) {
        for (const s of subs || []) {
          if (s.trim()) set.add(s.trim());
        }
      }
    }
    for (const n of existingNotes) {
      if (!cTrim || (n.course || '').trim().toLowerCase() === cTrim) {
        if (n.subject.trim()) set.add(n.subject.trim());
      }
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b));
  }, [course, registeredCoursesMap, existingNotes]);

  const isDirty = useMemo(() => {
    const cur = JSON.stringify({
      title,
      course,
      subject,
      rawContent,
      collectionType,
    });
    return Boolean(initialSnapshot && cur !== initialSnapshot);
  }, [title, course, subject, rawContent, collectionType, initialSnapshot]);

  const requestClose = React.useCallback(() => {
    if (isDirty && !confirmDiscard) {
      setConfirmDiscard(true);
      return;
    }
    onClose();
  }, [isDirty, confirmDiscard, onClose]);

  useEffect(() => {
    if (!onRegisterCloseRequest) return;
    if (!isOpen) {
      onRegisterCloseRequest(null);
      return;
    }
    onRegisterCloseRequest(requestClose);
    return () => onRegisterCloseRequest(null);
  }, [isOpen, requestClose, onRegisterCloseRequest]);

  if (!isOpen) return null;

  const handleTransform = (action: KnowledgeTransformAction) => {
    if (!rawContent.trim()) return;
    setUndoStack((prev) => [...prev.slice(-9), rawContent]);
    setRawContent(StudyNoteParser.applyTransform(rawContent, action));
  };

  const handleAppendText = (incoming: string) => {
    if (!incoming.trim()) return;
    const preview = StudyNoteParser.prepareAppendedContent(rawContent, incoming, syncedRule, true);
    if (!preview.appendSegment.trim()) {
      setAppendStatusMsg(`Skipped ${preview.skippedDuplicatesCount} duplicate item(s); nothing new to append.`);
      return;
    }
    setUndoStack((prev) => [...prev.slice(-9), rawContent]);
    setRawContent(preview.combinedContent);
    setAppendStatusMsg(
      `Appended +${preview.addedUnitCount} item(s)${
        preview.skippedDuplicatesCount > 0 ? ` (${preview.skippedDuplicatesCount} duplicates skipped)` : ''
      }.`
    );
    setInlineAppendDraft('');
    setShowInlineAppendBox(false);
  };

  const handleAppendClipboard = async () => {
    try {
      if (navigator.clipboard && typeof navigator.clipboard.readText === 'function') {
        const clip = await navigator.clipboard.readText();
        if (clip && clip.trim()) {
          handleAppendText(clip);
          return;
        }
      }
    } catch {
      // Fallback to inline paste box
    }
    setShowInlineAppendBox(true);
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    setUndoStack((s) => s.slice(0, -1));
    setRawContent(prev);
    setAppendStatusMsg('');
  };

  const handleSave = () => {
    if (!rawContent.trim()) {
      setErrorMsg('Please paste or enter study content before saving.');
      return;
    }
    const now = Date.now();
    const resolvedTitle =
      title.trim() ||
      (subject.trim()
        ? `${subject.trim()} ${KNOWLEDGE_COLLECTION_META[collectionType].label}`
        : 'Untitled Study Note');

    const record: StudyNoteRecord = {
      id: initialNote?.id || `note-${now}-${Math.random().toString(36).slice(2, 6)}`,
      title: resolvedTitle,
      course: course.trim(),
      subject: subject.trim() || 'General Studies',
      sourceAi: sourceAi.trim() || 'Manual Entry',
      rawContent: rawContent.trim(),
      folderId: folderId || 'default',
      collectionType,
      customLabel: collectionType === 'CUSTOM' ? customLabel.trim() : '',
      linkedPaperId: linkedPaperId || null,
      formatRule: syncedRule,
      bookmarkedIndices: initialNote?.bookmarkedIndices || [],
      masteredIndices: initialNote?.masteredIndices || [],
      srsStates: initialNote?.srsStates || {},
      createdAt: initialNote?.createdAt || now,
      updatedAt: now,
    };
    onSaveNote(record);
  };

  const TRANSFORMS: { action: KnowledgeTransformAction; label: string; title: string }[] = [
    { action: 'STRIP_AI_CHATTER', label: 'Strip AI Chatter', title: 'Remove conversational intros/fences' },
    { action: 'FIX_BACKWARDS_CHATGPT', label: 'Fix Backwards ->', title: 'Move short target terms to bold lead' },
    { action: 'SWAP_LEAD_DETAIL', label: 'Swap Lead <-> Detail', title: 'Invert left and right sides of arrows' },
    { action: 'MERGE_NESTED_BULLETS', label: 'Nest Sub-Bullets', title: 'Indent bullets under numbered parent facts' },
    { action: 'SPLIT_SEMICOLONS', label: 'Split Semicolons', title: 'Expand semicolon clauses into sub-bullets' },
  ];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto"
      onClick={requestClose}
    >
      <div
        className="w-full max-w-4xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[92vh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div
          className="px-5 py-4 border-b flex items-center justify-between gap-3"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5">
            <FileText className="w-5 h-5 text-emerald-300" />
            <div>
              <h3 className="text-base font-black" style={{ color: colors.navyBannerText }}>
                {initialNote ? 'Edit Study Note / Deck' : 'Create Study Note / SM-2 Deck'}
              </h3>
              <p className="text-[0.6875rem] opacity-80" style={{ color: colors.navyBannerText }}>
                Course → Subject → Notes • Live Preview &amp; Non-Destructive Append
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={requestClose}
            className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white transition"
            aria-label="Close Study Note Editor"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Unsaved Changes Prompt */}
        {confirmDiscard && (
          <div
            className="px-5 py-2.5 border-b flex flex-wrap items-center justify-between gap-2 text-xs"
            style={{ backgroundColor: colors.amberLight, borderColor: colors.amber }}
          >
            <span className="font-bold" style={{ color: colors.inkBold }}>
              You have unsaved changes. Discard edits and close?
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setConfirmDiscard(false)}
                className="px-3 py-1 rounded-lg border font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
              >
                Keep Editing
              </button>
              <button
                type="button"
                onClick={onClose}
                className="px-3 py-1 rounded-lg font-bold text-white"
                style={{ backgroundColor: colors.red }}
              >
                Discard
              </button>
            </div>
          </div>
        )}

        {/* Body */}
        <div className="p-5 overflow-y-auto space-y-4 text-xs flex-1">
          {errorMsg && (
            <div
              className="p-3 rounded-lg border font-semibold"
              style={{ backgroundColor: colors.redLight, borderColor: colors.red, color: colors.red }}
            >
              {errorMsg}
            </div>
          )}

          {/* Metadata Row */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Title
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g., Electrostatics High-Yield Sheet"
                className="w-full px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Course (Parent)
              </label>
              <input
                type="text"
                value={course}
                onChange={(e) => setCourse(e.target.value)}
                placeholder="e.g., UPSC CSE / JEE"
                list="editor-course-suggestions"
                className="w-full px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
              <datalist id="editor-course-suggestions">
                {existingCoursesList.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Subject / Topic
              </label>
              <input
                type="text"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g., Physics"
                list="editor-subject-suggestions"
                className="w-full px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
              <datalist id="editor-subject-suggestions">
                {subjectSuggestionsForCourse.map((s) => (
                  <option key={s} value={s} />
                ))}
              </datalist>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Collection Type
              </label>
              <select
                value={collectionType}
                onChange={(e) => setCollectionType(e.target.value as KnowledgeCollectionType)}
                className="w-full px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                {(Object.keys(KNOWLEDGE_COLLECTION_META) as KnowledgeCollectionType[]).map((key) => (
                  <option key={key} value={key}>
                    {KNOWLEDGE_COLLECTION_META[key].label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {existingCoursesList.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-[0.625rem] font-bold uppercase" style={{ color: colors.muted }}>
                Quick Courses:
              </span>
              {existingCoursesList.slice(0, 8).map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setCourse(c)}
                  className="px-2 py-0.5 rounded-full border text-[0.6875rem] font-bold"
                  style={
                    course.trim().toLowerCase() === c.toLowerCase()
                      ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }
                      : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
                  }
                >
                  {c}
                </button>
              ))}
            </div>
          )}

          {/* Secondary Metadata Row */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                Line / Layer Order
              </label>
              <select
                value={formatRule.layerOrder}
                onChange={(e) =>
                  setFormatRule({ ...formatRule, layerOrder: e.target.value as KnowledgeLayerOrder })
                }
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                <option value="AUTO_DETECT">Auto-Detect Order</option>
                <option value="TERM_FIRST">Term First (**Term** -&gt; Detail)</option>
                <option value="BACKWARDS_ANSWER_FIRST">Scenario Clue First (Clue -&gt; **Target**)</option>
                <option value="QA_SEPARATE_LINES">Q / A Separate Lines (Q: / A:)</option>
              </select>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                Linked Exam Paper (Optional)
              </label>
              <select
                value={linkedPaperId}
                onChange={(e) => setLinkedPaperId(e.target.value)}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                <option value="">None (Standalone Note)</option>
                {papers.map((p) => (
                  <option key={p.metadata.id} value={p.metadata.id}>
                    {p.metadata.title} ({p.questions.length} Qs)
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                Source AI / Reference
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={sourceAi}
                  onChange={(e) => setSourceAi(e.target.value)}
                  className="flex-1 px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
                {folders.length > 0 && (
                  <select
                    value={folderId}
                    onChange={(e) => setFolderId(e.target.value)}
                    className="px-2 py-1.5 rounded-lg border max-w-[160px]"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    title="Folder"
                  >
                    {folders
                      .filter((f) => f.id === 'default')
                      .map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.name}
                        </option>
                      ))}
                    {buildUserFolderSuggestions(folders).map((entry) => (
                      <option key={entry.id} value={entry.id}>
                        [{entry.levelLabel}] {entry.fullPath}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            </div>
          </div>

          {collectionType === 'CUSTOM' && (
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                Custom Collection Label
              </label>
              <input
                type="text"
                value={customLabel}
                onChange={(e) => setCustomLabel(e.target.value)}
                placeholder="e.g., Landmark Judgments"
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
            </div>
          )}

          {/* Normalization & Append Toolbar */}
          <div
            className="p-3 rounded-xl border space-y-2"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-bold flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                <Wand2 className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                AI Normalizers &amp; Append Tools
              </span>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => void handleAppendClipboard()}
                  className="flex items-center gap-1 px-2.5 py-1 rounded border font-bold text-[0.6875rem]"
                  style={{ backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }}
                >
                  <ClipboardPaste className="w-3 h-3" />
                  + Append Bottom
                </button>
                <select
                  value={formatRule.depth}
                  onChange={(e) => setFormatRule({ ...formatRule, depth: e.target.value as KnowledgeDepth })}
                  className="px-2 py-1 rounded border text-[0.6875rem]"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="CRISP">Depth: Crisp</option>
                  <option value="BALANCED">Depth: Balanced</option>
                  <option value="DEEP">Depth: Deep</option>
                </select>
                {undoStack.length > 0 && (
                  <button
                    type="button"
                    onClick={handleUndo}
                    className="flex items-center gap-1 px-2 py-1 rounded border font-bold text-[0.6875rem]"
                    style={{ backgroundColor: colors.surface, borderColor: colors.amber, color: colors.amber }}
                  >
                    <RotateCcw className="w-3 h-3" />
                    Undo
                  </button>
                )}
              </div>
            </div>

            <div className="flex flex-wrap gap-1.5">
              {TRANSFORMS.map((tr) => (
                <button
                  key={tr.action}
                  type="button"
                  onClick={() => handleTransform(tr.action)}
                  title={tr.title}
                  className="px-2.5 py-1 rounded-lg border font-semibold transition text-[0.6875rem]"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                >
                  {tr.label}
                </button>
              ))}
            </div>

            {appendStatusMsg && (
              <div className="text-[0.6875rem] font-bold" style={{ color: colors.tealDark }}>
                {appendStatusMsg}
              </div>
            )}

            {showInlineAppendBox && (
              <div
                className="p-2.5 rounded-lg border space-y-2"
                style={{ backgroundColor: colors.surface, borderColor: colors.teal }}
              >
                <div className="text-[0.6875rem] font-bold" style={{ color: colors.inkBold }}>
                  Paste lines to append cleanly with duplicate filtering &amp; sequential numbering:
                </div>
                <textarea
                  value={inlineAppendDraft}
                  onChange={(e) => setInlineAppendDraft(e.target.value)}
                  rows={4}
                  placeholder="Paste new study points or Q&A items..."
                  className="w-full p-2 rounded border font-mono text-xs"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowInlineAppendBox(false)}
                    className="px-2.5 py-1 rounded border font-semibold"
                    style={{ borderColor: colors.border, color: colors.muted }}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={() => handleAppendText(inlineAppendDraft)}
                    className="px-3 py-1 rounded font-bold"
                    style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                  >
                    Merge &amp; Renumber
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Content Textarea / Live Preview */}
          <div>
            <div className="flex flex-wrap items-center justify-between gap-2 mb-1.5">
              <div className="flex items-center gap-2">
                <label className="font-bold flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                  <Sparkles className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                  Markdown / Knowledge Content
                </label>
                <div
                  className="flex rounded-lg border p-0.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <button
                    type="button"
                    onClick={() => setEditorMode('WRITE')}
                    className="flex items-center gap-1 px-2.5 py-0.5 rounded font-bold text-[0.6875rem]"
                    style={
                      editorMode === 'WRITE'
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                        : { color: colors.ink }
                    }
                  >
                    <Edit3 className="w-3 h-3" />
                    Write
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditorMode('PREVIEW')}
                    className="flex items-center gap-1 px-2.5 py-0.5 rounded font-bold text-[0.6875rem]"
                    style={
                      editorMode === 'PREVIEW'
                        ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                        : { color: colors.ink }
                    }
                  >
                    <Eye className="w-3 h-3" />
                    Preview
                  </button>
                </div>
              </div>
              <div className="flex items-center gap-2 text-[0.6875rem]" style={{ color: colors.muted }}>
                {sourceHint && (
                  <span
                    className="px-2 py-0.5 rounded-full font-bold border"
                    style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                  >
                    {sourceHint}
                  </span>
                )}
                <span className="font-semibold">
                  {parsedCount} recall items • {parsedBlocks.length} total blocks
                </span>
              </div>
            </div>

            {editorMode === 'WRITE' ? (
              <>
                <div
                  className="px-3 py-2 rounded-lg border font-mono text-[0.6875rem] leading-snug overflow-x-auto whitespace-nowrap mb-1.5"
                  style={{ backgroundColor: colors.blueLight, borderColor: colors.border, color: colors.inkMuted }}
                >
                  Syntax cue: <code>## Topic</code> • <code>1. **Term** -&gt; Meaning</code> • <code>  - Formula: $...$</code>
                </div>
                <textarea
                  value={rawContent}
                  onChange={(e) => {
                    setRawContent(e.target.value);
                    if (errorMsg) setErrorMsg('');
                  }}
                  rows={11}
                  placeholder="Paste structured notes, Q&A decks, formulas, or Markdown tables..."
                  className="w-full min-h-[14rem] resize-y p-3 rounded-xl border font-mono text-xs leading-relaxed"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </>
            ) : (
              <div
                className="p-3.5 rounded-xl border max-h-[20rem] overflow-y-auto space-y-2.5"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                {parsedBlocks.length === 0 ? (
                  <p className="text-center py-6" style={{ color: colors.muted }}>
                    Nothing to preview yet.
                  </p>
                ) : (
                  parsedBlocks.map((b) => {
                    if (b.kind === 'heading') {
                      return (
                        <h4 key={b.blockIndex} className="text-sm font-black border-b pb-1" style={{ borderColor: colors.teal, color: colors.inkBold }}>
                          <LatexText text={b.text} />
                        </h4>
                      );
                    }
                    if (b.kind === 'table') {
                      return <MarkdownTableCard key={b.blockIndex} table={{ headers: b.headers, rows: b.rows }} />;
                    }
                    if (b.kind === 'callout') {
                      return (
                        <div key={b.blockIndex} className="p-2.5 rounded-lg border-l-4" style={{ backgroundColor: colors.blueLight, borderColor: colors.teal }}>
                          <strong>{b.label}:</strong> <LatexText text={b.body} />
                        </div>
                      );
                    }
                    if (b.kind === 'one_liner') {
                      return (
                        <div key={b.blockIndex} className="p-2.5 rounded-lg border" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
                          <strong style={{ color: colors.teal }}>{b.displayIndex}.</strong>{' '}
                          {b.leadTerm && (
                            <>
                              <strong><LatexText text={b.leadTerm} /></strong> →{' '}
                            </>
                          )}
                          <LatexText text={b.detail} />
                        </div>
                      );
                    }
                    if (b.kind === 'multi_liner_card') {
                      return (
                        <div key={b.blockIndex} className="p-2.5 rounded-lg border space-y-1" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
                          <div className="font-bold" style={{ color: colors.inkBold }}>
                            <span style={{ color: colors.teal }}>{b.displayIndex}</span> <LatexText text={b.title} />
                          </div>
                          {b.paragraphs.map((p, idx) => (
                            <div key={idx}><LatexText text={p} /></div>
                          ))}
                        </div>
                      );
                    }
                    return (
                      <div key={b.blockIndex} className="p-2 rounded-lg" style={{ color: colors.ink }}>
                        <LatexText text={b.text} />
                      </div>
                    );
                  })
                )}
              </div>
            )}
          </div>

          {/* Rule Flags */}
          <div className="flex flex-wrap gap-4 pt-1">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={formatRule.preserveNestedBullets}
                onChange={(e) => setFormatRule({ ...formatRule, preserveNestedBullets: e.target.checked })}
              />
              <span style={{ color: colors.ink }}>Preserve Nested Sub-Bullets</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={formatRule.includeLatexMath}
                onChange={(e) => setFormatRule({ ...formatRule, includeLatexMath: e.target.checked })}
              />
              <span style={{ color: colors.ink }}>Render LaTeX Math</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={formatRule.includeCallouts}
                onChange={(e) => setFormatRule({ ...formatRule, includeCallouts: e.target.checked })}
              />
              <span style={{ color: colors.ink }}>Render Callout Blocks</span>
            </label>
          </div>
        </div>

        {/* Footer */}
        <div
          className="px-5 py-3.5 border-t flex items-center justify-end gap-2"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <button
            type="button"
            onClick={requestClose}
            className="px-4 py-2 rounded-lg border text-xs font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold shadow-sm"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <Save className="w-4 h-4" />
            Save Study Note
          </button>
        </div>
      </div>
    </div>
  );
};

export default StudyNoteEditorModal;
