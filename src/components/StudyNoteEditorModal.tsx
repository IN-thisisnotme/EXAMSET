import React, { useEffect, useMemo, useState } from 'react';
import {
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
  KnowledgeDepth,
  KnowledgeFormatRule,
  KnowledgeLayerOrder,
  KnowledgeTransformAction,
  StudyNoteRecord,
} from '../types/phase4';
import { StudyNoteParser } from '../utils/studyNoteParser';

interface StudyNoteEditorModalProps {
  isOpen: boolean;
  initialNote?: StudyNoteRecord | null;
  prefill?: {
    collectionType?: KnowledgeCollectionType;
    subject?: string;
    topic?: string;
  } | null;
  papers: QuestionPaper[];
  folders: FolderItem[];
  onSaveNote: (note: StudyNoteRecord) => void;
  onClose: () => void;
}

export const StudyNoteEditorModal: React.FC<StudyNoteEditorModalProps> = ({
  isOpen,
  initialNote,
  prefill,
  papers,
  folders,
  onSaveNote,
  onClose,
}) => {
  const { colors } = useTheme();
  const [title, setTitle] = useState<string>('');
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

  useEffect(() => {
    if (!isOpen) return;
    if (initialNote) {
      setTitle(initialNote.title);
      setSubject(initialNote.subject);
      setSourceAi(initialNote.sourceAi || 'Custom');
      setFolderId(initialNote.folderId || 'default');
      setCollectionType(initialNote.collectionType);
      setCustomLabel(initialNote.customLabel || '');
      setLinkedPaperId(initialNote.linkedPaperId || '');
      setFormatRule(initialNote.formatRule || { ...DEFAULT_KNOWLEDGE_RULE, collectionType: initialNote.collectionType });
      setRawContent(initialNote.rawContent);
    } else {
      const cType = prefill?.collectionType || 'MULTI_LINER';
      setTitle(prefill?.topic ? `${prefill.topic} Notes` : '');
      setSubject(prefill?.subject || '');
      setSourceAi('AI / Study Import');
      setFolderId('default');
      setCollectionType(cType);
      setCustomLabel('');
      setLinkedPaperId('');
      setFormatRule({
        ...DEFAULT_KNOWLEDGE_RULE,
        collectionType: cType,
        layerOrder: cType === 'FLASHCARDS' ? 'QA_SEPARATE_LINES' : 'AUTO_DETECT',
      });
      setRawContent('');
    }
    setUndoStack([]);
    setErrorMsg('');
  }, [isOpen, initialNote, prefill]);

  const syncedRule = useMemo<KnowledgeFormatRule>(
    () => ({
      ...formatRule,
      collectionType,
      customCollectionLabel: customLabel,
    }),
    [formatRule, collectionType, customLabel]
  );

  const parsedCount = useMemo(
    () => (rawContent.trim() ? StudyNoteParser.countStudyItems(rawContent, syncedRule) : 0),
    [rawContent, syncedRule]
  );

  const totalBlocksCount = useMemo(
    () => (rawContent.trim() ? StudyNoteParser.parseBlocks(rawContent, syncedRule).length : 0),
    [rawContent, syncedRule]
  );

  const sourceHint = useMemo(
    () => (rawContent.trim() ? StudyNoteParser.detectSourceHint(rawContent) : ''),
    [rawContent]
  );

  if (!isOpen) return null;

  const handleTransform = (action: KnowledgeTransformAction) => {
    if (!rawContent.trim()) return;
    setUndoStack((prev) => [...prev.slice(-9), rawContent]);
    setRawContent(StudyNoteParser.applyTransform(rawContent, action));
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    setUndoStack((s) => s.slice(0, -1));
    setRawContent(prev);
  };

  const handleSave = () => {
    if (!rawContent.trim()) {
      setErrorMsg('Please paste or enter study content before saving.');
      return;
    }
    const now = Date.now();
    const resolvedTitle =
      title.trim() ||
      (subject.trim() ? `${subject.trim()} ${KNOWLEDGE_COLLECTION_META[collectionType].label}` : 'Untitled Study Note');

    const record: StudyNoteRecord = {
      id: initialNote?.id || `note-${now}-${Math.random().toString(36).slice(2, 6)}`,
      title: resolvedTitle,
      subject: subject.trim() || 'General Studies',
      sourceAi: sourceAi.trim() || 'Manual Entry',
      rawContent: rawContent.trim(),
      folderId: folderId || 'default',
      collectionType,
      customLabel: collectionType === 'CUSTOM' ? customLabel.trim() : '',
      linkedPaperId: linkedPaperId || null,
      formatRule: syncedRule,
      bookmarkedIndices: initialNote?.bookmarkedIndices || [],
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto">
      <div
        className="w-full max-w-4xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[92vh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
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
                Supports One-Liners, Multi-Liner Cards, Q&amp;A Flashcards, Markdown Tables, Callouts &amp; LaTeX Math
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white transition"
            aria-label="Close Study Note Editor"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

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
            <div className="sm:col-span-2">
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
                Subject / Topic
              </label>
              <input
                type="text"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g., Physics"
                className="w-full px-3 py-2 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
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

          {/* Normalization Transform Toolbar */}
          <div
            className="p-3 rounded-xl border space-y-2"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-bold flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                <Wand2 className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                One-Click AI Content Normalizers
              </span>
              <div className="flex items-center gap-2">
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
                    Undo Transform
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
          </div>

          {/* Content Textarea */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="font-bold flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                <Sparkles className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                Markdown / Knowledge Content
              </label>
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
                  {parsedCount} recall items • {totalBlocksCount} total blocks
                </span>
              </div>
            </div>

            <div
              className="px-3 py-2 rounded-lg border font-mono text-[0.6875rem] leading-snug overflow-x-auto whitespace-nowrap"
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
            onClick={onClose}
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
