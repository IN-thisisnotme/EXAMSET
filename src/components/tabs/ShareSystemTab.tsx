import React, { useMemo, useState } from 'react';
import {
  Check,
  CheckCircle2,
  Copy,
  Download,
  FileJson,
  Layers,
  Link,
  Share2,
  Upload,
} from 'lucide-react';
import { QuestionPaper } from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { BatchExamImporter } from '../../utils/batchExamImporter';
import { SvgQrGenerator } from '../../utils/qrCodeHelper';

interface ShareSystemTabProps {
  currentPaper: QuestionPaper;
  allPapers?: QuestionPaper[];
  onImportPaper: (paper: QuestionPaper) => void;
  onImportPapers?: (papers: QuestionPaper[]) => void;
  onOpenBatchImport?: () => void;
}

export const ShareSystemTab: React.FC<ShareSystemTabProps> = ({
  currentPaper,
  allPapers = [],
  onImportPaper,
  onImportPapers,
  onOpenBatchImport,
}) => {
  const { colors } = useTheme();
  const [copiedLink, setCopiedLink] = useState<boolean>(false);
  const [copiedJson, setCopiedJson] = useState<boolean>(false);
  const [importJsonText, setImportJsonText] = useState<string>('');
  const [importError, setImportError] = useState<string | null>(null);
  const [importSuccess, setImportSuccess] = useState<string | null>(null);

  const sharePayload = useMemo(() => JSON.stringify(currentPaper, null, 2), [currentPaper]);

  const offlineKeyQrSvg = useMemo(() => {
    const payload = SvgQrGenerator.generatePayload(
      currentPaper.metadata.id,
      currentPaper.metadata.subject,
      currentPaper.metadata.setNumber,
      String(currentPaper.metadata.durationMinutes || 120),
      String(currentPaper.questions.length * (currentPaper.metadata.positiveMarks || 2)),
      currentPaper.questions.slice(0, 200)
    );
    return SvgQrGenerator.generateSvg(payload, 18, colors.navyBanner);
  }, [currentPaper, colors.navyBanner]);

  const handleCopyLink = () => {
    navigator.clipboard?.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2400);
  };

  const handleCopyJson = () => {
    navigator.clipboard?.writeText(sharePayload);
    setCopiedJson(true);
    setTimeout(() => setCopiedJson(false), 2400);
  };

  const downloadJson = (filename: string, content: string) => {
    const blob = new Blob([content], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleDownloadSingleJson = () => {
    downloadJson(
      `${currentPaper.metadata.title.replace(/\s+/g, '_')}.examset.json`,
      sharePayload
    );
  };

  const handleDownloadLibraryBundle = () => {
    const bundle = {
      schemaVersion: 2,
      exportedAt: new Date().toISOString(),
      papers: allPapers.length > 0 ? allPapers : [currentPaper],
    };
    downloadJson(
      `ExamSet_Library_Bundle_${new Date().toISOString().slice(0, 10)}.json`,
      JSON.stringify(bundle, null, 2)
    );
  };

  const processImportedText = (rawText: string) => {
    setImportError(null);
    setImportSuccess(null);

    if (!rawText.trim()) {
      setImportError('Please paste valid ExamSet JSON content.');
      return;
    }

    const folderId = currentPaper.metadata.folderId || 'default';
    const parsedItems = BatchExamImporter.validateRawContent(rawText, 'pasted_import.json', rawText.length);
    const validPapers = parsedItems
      .map((item) => item.paper)
      .filter((p): p is QuestionPaper => p !== null)
      .map((p) => ({
        ...p,
        metadata: {
          ...p.metadata,
          folderId: p.metadata.folderId || folderId,
        },
      }));

    if (validPapers.length === 0) {
      const firstErr = parsedItems[0]?.issues.find((x) => x.severity === 'ERROR')?.message;
      setImportError(firstErr || 'Could not parse any valid ExamSet paper from the JSON payload.');
      return;
    }

    if (validPapers.length > 1 && onImportPapers) {
      onImportPapers(validPapers);
      setImportSuccess(`Imported ${validPapers.length} papers into your workspace library.`);
    } else {
      onImportPaper(validPapers[0]);
      setImportSuccess(
        `Imported "${validPapers[0].metadata.title}" (${validPapers[0].questions.length} questions).`
      );
    }
    setImportJsonText('');
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    if (files.length === 1) {
      const text = await files[0].text();
      processImportedText(text);
    } else if (onOpenBatchImport) {
      onOpenBatchImport();
    } else {
      const text = await files[0].text();
      processImportedText(text);
    }
    e.target.value = '';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div
        className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 sm:p-5 rounded-2xl border"
        style={{ background: colors.heroGradient, borderColor: colors.border, boxShadow: colors.cardShadow }}
      >
        <div className="min-w-0">
          <h3 className="text-base font-black flex items-center gap-2 tracking-tight" style={{ color: colors.inkBold }}>
            <Share2 className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            <span className="es-clamp-2">Share, Bundle Export &amp; Peer Import Hub</span>
          </h3>
          <p className="text-xs mt-1 es-scroll-x" style={{ color: colors.muted }}>
            Export single assessments (.examset.json), full library bundles, or scan/import peer packages seamlessly.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {allPapers.length > 1 && (
            <button
              onClick={handleDownloadLibraryBundle}
              className="flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold rounded-lg border transition"
              style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
            >
              <Layers className="w-3.5 h-3.5" style={{ color: colors.teal }} />
              Export All ({allPapers.length} Sets)
            </button>
          )}
          <button
            onClick={handleDownloadSingleJson}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg shadow-sm transition"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <Download className="w-4 h-4" />
            Download .examset.json
          </button>
        </div>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-6 space-y-4">
          {/* Distribution Identity & Offline Key QR */}
          <div
            className="rounded-xl border p-5 space-y-4"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
              <Link className="w-3.5 h-3.5" />
              Workspace Link &amp; Machine Grading QR
            </h4>

            <div className="flex items-center gap-2 min-w-0">
              <input
                type="text"
                readOnly
                value={window.location.href}
                className="min-w-0 flex-1 text-xs font-mono border rounded-lg px-3 py-2 focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }}
              />
              <button
                onClick={handleCopyLink}
                className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg flex-shrink-0"
                style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
              >
                {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedLink ? 'Copied' : 'Copy'}</span>
              </button>
            </div>

            <div
              className="p-4 rounded-xl border flex items-center justify-between gap-4"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="space-y-1 min-w-0">
                <span className="text-[0.6875rem] font-bold uppercase tracking-wider" style={{ color: colors.teal }}>
                  Offline Grading Matrix Ready
                </span>
                <h5 className="text-sm font-bold truncate" style={{ color: colors.inkBold }}>
                  {currentPaper.metadata.title}
                </h5>
                <p className="text-xs" style={{ color: colors.muted }}>
                  {currentPaper.metadata.subject} • {currentPaper.questions.length} Items • Set {currentPaper.metadata.setNumber}
                </p>
              </div>

              <div
                className="p-1.5 rounded-lg border bg-white flex-shrink-0 shadow-sm"
                style={{ borderColor: colors.border }}
                dangerouslySetInnerHTML={{ __html: offlineKeyQrSvg }}
                title="Serialized OMR Offline Answer Key QR"
              />
            </div>
          </div>

          {/* Import Card */}
          <div
            className="rounded-xl border p-5 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
                <Upload className="w-3.5 h-3.5" />
                Import Paper or Multi-Paper Bundle
              </h4>
              {onOpenBatchImport && (
                <button
                  onClick={onOpenBatchImport}
                  className="text-[0.6875rem] font-bold"
                  style={{ color: colors.teal }}
                >
                  Open Multi-File Batch Importer →
                </button>
              )}
            </div>

            <textarea
              value={importJsonText}
              onChange={(e) => setImportJsonText(e.target.value)}
              placeholder="Paste single .examset.json or multi-paper bundle JSON here..."
              rows={5}
              className="w-full min-h-[8rem] resize-y p-3 font-mono text-xs border rounded-lg leading-relaxed focus:outline-none"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
            />

            {importError && (
              <div
                className="p-2.5 rounded-lg text-xs font-semibold"
                style={{ backgroundColor: colors.badgeDangerBg, color: colors.badgeDangerText }}
              >
                {importError}
              </div>
            )}

            {importSuccess && (
              <div
                className="p-2.5 rounded-lg text-xs font-semibold flex items-center gap-1.5"
                style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
              >
                <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
                <span>{importSuccess}</span>
              </div>
            )}

            <div className="flex items-center justify-between gap-2 flex-wrap">
              <label
                className="cursor-pointer flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                <Upload className="w-3.5 h-3.5" />
                <span>Choose .json File</span>
                <input type="file" accept=".json,.txt" onChange={handleFileUpload} className="hidden" />
              </label>

              <button
                onClick={() => processImportedText(importJsonText)}
                className="px-4 py-1.5 text-xs font-bold rounded-lg shadow-sm"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Load into Workspace
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: JSON Payload Inspector */}
        <div className="lg:col-span-6 min-w-0">
          <div className="bg-slate-900 rounded-xl p-4 sm:p-5 shadow-lg border border-slate-800 flex flex-col h-full min-h-[300px] lg:min-h-[500px]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3 text-xs text-slate-400">
              <div className="flex items-center gap-2 font-mono text-slate-200">
                <FileJson className="w-4 h-4 text-emerald-400" />
                <span>ExamSet Standard JSON Contract</span>
              </div>
              <button
                onClick={handleCopyJson}
                className="flex items-center gap-1 text-slate-300 hover:text-white transition"
              >
                {copiedJson ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                <span>{copiedJson ? 'Copied' : 'Copy JSON'}</span>
              </button>
            </div>

            <textarea
              readOnly
              value={sharePayload}
              className="flex-1 w-full bg-transparent text-emerald-400 font-mono text-[0.6875rem] leading-relaxed border-0 resize-none focus:outline-none"
            />

            <div className="pt-3 border-t border-slate-800 flex items-center justify-between text-[0.6875rem] text-slate-400">
              <span>Includes metadata, MSQ keys, Option E, formulas &amp; explanations</span>
              <button onClick={handleDownloadSingleJson} className="font-semibold text-sky-400 hover:text-sky-300">
                Download File
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ShareSystemTab;
