import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckSquare,
  Download,
  ExternalLink,
  Eye,
  FileCheck,
  MonitorPlay,
  Printer,
  RefreshCw,
  Settings,
  Sparkles,
} from 'lucide-react';
import {
  DEFAULT_EXAM_CONFIG,
  ExamConfig,
  MsqScoringPolicy,
  OmrBubblingMode,
  PrintMode,
  QuestionPaper,
} from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { ExamNumberGenerator } from '../../utils/examNumberGenerator';
import { PrintDocumentHelper } from '../../utils/printDocumentHelper';

interface PrintGeneratorTabProps {
  currentPaper: QuestionPaper;
  allPapers?: QuestionPaper[];
}

export const PrintGeneratorTab: React.FC<PrintGeneratorTabProps> = ({
  currentPaper,
  allPapers = [],
}) => {
  const { colors } = useTheme();

  const [config, setConfig] = useState<ExamConfig>(() => {
    const pos = currentPaper.metadata.positiveMarks ?? 2.0;
    const count = Math.max(1, currentPaper.questions.length);
    const hasE = currentPaper.questions.some((q) => Boolean(q.options.e?.trim()));
    return {
      ...DEFAULT_EXAM_CONFIG,
      examTitle: currentPaper.metadata.title || 'ALL INDIA OBJECTIVE EXAMINATION',
      subject: currentPaper.metadata.subject || 'General Studies',
      topic: currentPaper.metadata.topic || '',
      seriesCode: ExamNumberGenerator.normalizeSetCode(currentPaper.metadata.setNumber),
      rollNumber: ExamNumberGenerator.normalizeRollNumber(currentPaper.metadata.rollNumber),
      bookletNumber: ExamNumberGenerator.normalizeBookletNumber(currentPaper.metadata.bookletNumber),
      durationMinutes: currentPaper.metadata.durationMinutes ?? Math.max(30, count * 2),
      timeAllowed: `${currentPaper.metadata.durationMinutes ?? Math.max(30, count * 2)} Minutes`,
      maxMarks: `${(count * pos).toFixed(0)} Marks`,
      positiveMarks: pos,
      negativeMarks: currentPaper.metadata.negativeMarks ?? 0.66,
      unattendedMarks: currentPaper.metadata.unattendedMarks ?? 0.0,
      msqScoringPolicy: currentPaper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING',
      omrOptionCount: hasE ? 5 : 4,
    };
  });

  // Sync key metadata changes when switching active paper
  useEffect(() => {
    const count = Math.max(1, currentPaper.questions.length);
    const pos = currentPaper.metadata.positiveMarks ?? 2.0;
    const hasE = currentPaper.questions.some((q) => Boolean(q.options.e?.trim()));
    setConfig((prev) => ({
      ...prev,
      examTitle: currentPaper.metadata.title || prev.examTitle,
      subject: currentPaper.metadata.subject || prev.subject,
      topic: currentPaper.metadata.topic || prev.topic,
      seriesCode: ExamNumberGenerator.normalizeSetCode(currentPaper.metadata.setNumber),
      rollNumber: ExamNumberGenerator.normalizeRollNumber(currentPaper.metadata.rollNumber),
      bookletNumber: ExamNumberGenerator.normalizeBookletNumber(currentPaper.metadata.bookletNumber),
      durationMinutes: currentPaper.metadata.durationMinutes ?? prev.durationMinutes,
      positiveMarks: pos,
      negativeMarks: currentPaper.metadata.negativeMarks ?? prev.negativeMarks,
      unattendedMarks: currentPaper.metadata.unattendedMarks ?? prev.unattendedMarks,
      msqScoringPolicy: currentPaper.metadata.msqScoringPolicy || prev.msqScoringPolicy,
      maxMarks: `${(count * pos).toFixed(0)} Marks`,
      omrOptionCount: hasE ? 5 : prev.omrOptionCount,
    }));
  }, [currentPaper.metadata.id, currentPaper.questions.length]);

  const [activeMode, setActiveMode] = useState<PrintMode>('paper_and_omr');
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const generatedHtml = useMemo(() => {
    return PrintDocumentHelper.generatePrintHtml(currentPaper, {
      ...config,
      printMode: activeMode,
    });
  }, [currentPaper, config, activeMode]);

  const handlePrint = () => {
    const iframe = iframeRef.current;
    if (iframe && iframe.contentWindow) {
      iframe.contentWindow.focus();
      iframe.contentWindow.print();
    } else {
      const win = window.open('', '_blank');
      if (win) {
        win.document.open();
        win.document.write(generatedHtml);
        win.document.close();
        win.focus();
        setTimeout(() => win.print(), 300);
      }
    }
  };

  const downloadFile = (filename: string, content: string) => {
    const blob = new Blob([content], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleDownloadPrintableHtml = () => {
    const slug = (currentPaper.metadata.subject || 'Exam').replace(/[^a-zA-Z0-9]+/g, '_');
    downloadFile(`${slug}_${config.rollNumber}_${config.bookletNumber}_${activeMode}.html`, generatedHtml);
  };

  const handleDownloadInteractiveCbt = () => {
    const html = PrintDocumentHelper.generateInteractiveExamHtml(currentPaper, config);
    const slug = (currentPaper.metadata.title || 'ExamSet').replace(/[^a-zA-Z0-9]+/g, '_');
    downloadFile(`${slug}_Interactive_CBT.html`, html);
  };

  const handleOpenInNewTab = () => {
    const win = window.open();
    if (win) {
      win.document.open();
      win.document.write(generatedHtml);
      win.document.close();
    }
  };

  const handleAutoNextRoll = () => {
    const existing = [
      config.rollNumber,
      ...allPapers.map((p) => p.metadata.rollNumber || ''),
    ].filter(Boolean);
    setConfig((prev) => ({
      ...prev,
      rollNumber: ExamNumberGenerator.nextRollNumber(existing),
    }));
  };

  const handleAutoNextBooklet = () => {
    const existing = [
      config.bookletNumber,
      ...allPapers.map((p) => p.metadata.bookletNumber || ''),
    ].filter(Boolean);
    setConfig((prev) => ({
      ...prev,
      bookletNumber: ExamNumberGenerator.nextBookletNumber(existing),
    }));
  };

  return (
    <div className="space-y-6">
      {/* Header Strip */}
      <div
        className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-4 rounded-xl border"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
            <Printer className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            Print &amp; OMR Studio • A4 Booklet &amp; CBT Exporter
          </h3>
          <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
            Generates ISO A4 question booklets, drop-out magenta OMR sheets (4 or 5 options, up to 200 items), and offline HTML CBT apps.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={handleDownloadInteractiveCbt}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold rounded-lg border transition"
            style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
          >
            <MonitorPlay className="w-3.5 h-3.5" />
            Export Offline CBT (.html)
          </button>

          <button
            onClick={handleDownloadPrintableHtml}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border transition"
            style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
          >
            <Download className="w-3.5 h-3.5" />
            Download Print HTML
          </button>

          <button
            onClick={handleOpenInNewTab}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border transition"
            style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
          >
            <ExternalLink className="w-3.5 h-3.5" />
            Full Tab
          </button>

          <button
            onClick={handlePrint}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg shadow transition"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <Printer className="w-4 h-4" />
            Print / Save PDF
          </button>
        </div>
      </div>

      {/* Mode Selection Pills */}
      <div className="flex items-center gap-2 border-b pb-3 overflow-x-auto" style={{ borderColor: colors.border }}>
        {(
          [
            { id: 'paper_and_omr', label: 'Paper + 1-Page OMR', icon: <FileCheck className="w-4 h-4" /> },
            { id: 'paper_only', label: 'Student Paper Only', icon: <FileCheck className="w-4 h-4" /> },
            { id: 'omr_only', label: 'OMR Sheet Only (A4)', icon: <CheckSquare className="w-4 h-4" /> },
            { id: 'master_booklet', label: 'Master Evaluation Booklet', icon: <Eye className="w-4 h-4" /> },
            { id: 'solutions_only', label: 'Answer Key & Solutions', icon: <Sparkles className="w-4 h-4" /> },
          ] as { id: PrintMode; label: string; icon: React.ReactNode }[]
        ).map((m) => {
          const active = activeMode === m.id;
          return (
            <button
              key={m.id}
              onClick={() => setActiveMode(m.id)}
              className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition whitespace-nowrap border"
              style={
                active
                  ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                  : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
              }
            >
              {m.icon}
              <span>{m.label}</span>
            </button>
          );
        })}
      </div>

      {/* Main Split View */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Settings Sidebar */}
        <div className="lg:col-span-4 space-y-4">
          <div
            className="rounded-xl border p-4 space-y-3 text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <h4 className="font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
              <Settings className="w-3.5 h-3.5" />
              Booklet &amp; OMR Configuration
            </h4>

            <div>
              <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                Institution / Board Name
              </label>
              <input
                type="text"
                value={config.institutionName}
                onChange={(e) => setConfig({ ...config, institutionName: e.target.value })}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
            </div>

            <div>
              <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                Examination Title
              </label>
              <input
                type="text"
                value={config.examTitle}
                onChange={(e) => setConfig({ ...config, examTitle: e.target.value })}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Set Code (2 Digits)
                </label>
                <input
                  type="text"
                  value={config.seriesCode}
                  onChange={(e) => setConfig({ ...config, seriesCode: e.target.value.slice(0, 4) })}
                  className="w-full px-3 py-1.5 rounded-lg border font-mono"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Duration (Min)
                </label>
                <input
                  type="number"
                  min={5}
                  max={360}
                  value={config.durationMinutes}
                  onChange={(e) => {
                    const mins = Math.max(5, Number(e.target.value) || 60);
                    setConfig({ ...config, durationMinutes: mins, timeAllowed: `${mins} Minutes` });
                  }}
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
            </div>

            {/* Roll & Booklet Number with Auto-Next */}
            <div className="grid grid-cols-1 gap-2 pt-2 border-t" style={{ borderColor: colors.borderLight }}>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="font-semibold" style={{ color: colors.ink }}>
                    Roll Number (10 Digits)
                  </label>
                  <button
                    type="button"
                    onClick={handleAutoNextRoll}
                    className="inline-flex items-center gap-1 text-[10px] font-bold"
                    style={{ color: colors.teal }}
                  >
                    <RefreshCw className="w-3 h-3" />
                    Auto Next
                  </button>
                </div>
                <input
                  type="text"
                  value={config.rollNumber}
                  onChange={(e) => setConfig({ ...config, rollNumber: e.target.value.replace(/\D/g, '').slice(0, 10) })}
                  className="w-full px-3 py-1.5 rounded-lg border font-mono"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="font-semibold" style={{ color: colors.ink }}>
                    Booklet Number (7 Digits)
                  </label>
                  <button
                    type="button"
                    onClick={handleAutoNextBooklet}
                    className="inline-flex items-center gap-1 text-[10px] font-bold"
                    style={{ color: colors.teal }}
                  >
                    <RefreshCw className="w-3 h-3" />
                    Auto Next
                  </button>
                </div>
                <input
                  type="text"
                  value={config.bookletNumber}
                  onChange={(e) =>
                    setConfig({ ...config, bookletNumber: e.target.value.replace(/\D/g, '').slice(0, 7) })
                  }
                  className="w-full px-3 py-1.5 rounded-lg border font-mono"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
            </div>

            {/* OMR Mode & Option Count */}
            <div className="grid grid-cols-2 gap-2 pt-2 border-t" style={{ borderColor: colors.borderLight }}>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  OMR Bubble Mode
                </label>
                <select
                  value={config.omrBubblingMode}
                  onChange={(e) =>
                    setConfig({ ...config, omrBubblingMode: e.target.value as OmrBubblingMode })
                  }
                  className="w-full px-2 py-1.5 rounded-lg border text-[11px]"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="BLANK_CANDIDATE">Blank Boxes</option>
                  <option value="PRE_PRINTED_DIGITS">Printed Digits</option>
                  <option value="PRE_DARKENED_BUBBLES">Darkened Bubbles</option>
                </select>
              </div>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  OMR Options / Q
                </label>
                <select
                  value={config.omrOptionCount}
                  onChange={(e) =>
                    setConfig({ ...config, omrOptionCount: Number(e.target.value) === 5 ? 5 : 4 })
                  }
                  className="w-full px-2 py-1.5 rounded-lg border text-[11px]"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value={4}>4 Options (A–D)</option>
                  <option value={5}>5 Options (A–E)</option>
                </select>
              </div>
            </div>

            {/* Marking Scheme */}
            <div className="grid grid-cols-3 gap-2 pt-2 border-t" style={{ borderColor: colors.borderLight }}>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>+Correct</label>
                <input
                  type="number"
                  step="0.25"
                  value={config.positiveMarks}
                  onChange={(e) => setConfig({ ...config, positiveMarks: Number(e.target.value) || 0 })}
                  className="w-full px-2 py-1 rounded border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>-Wrong</label>
                <input
                  type="number"
                  step="0.05"
                  value={config.negativeMarks}
                  onChange={(e) => setConfig({ ...config, negativeMarks: Math.max(0, Number(e.target.value) || 0) })}
                  className="w-full px-2 py-1 rounded border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>MSQ Rule</label>
                <select
                  value={config.msqScoringPolicy}
                  onChange={(e) =>
                    setConfig({ ...config, msqScoringPolicy: e.target.value as MsqScoringPolicy })
                  }
                  className="w-full px-1.5 py-1 rounded border text-[10px]"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="ALL_OR_NOTHING">Strict</option>
                  <option value="PARTIAL_STRICT">Partial</option>
                </select>
              </div>
            </div>

            {/* Layout Toggles */}
            <div className="pt-2 border-t space-y-2" style={{ borderColor: colors.borderLight }}>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.twoColumns}
                  onChange={(e) => setConfig({ ...config, twoColumns: e.target.checked })}
                />
                <span>Two-Column Question Paper Flow</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.includeCandidateDetails}
                  onChange={(e) => setConfig({ ...config, includeCandidateDetails: e.target.checked })}
                />
                <span>Include Candidate Declaration Box</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.includeInstructions}
                  onChange={(e) => setConfig({ ...config, includeInstructions: e.target.checked })}
                />
                <span>Include Instructions Block</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.showWatermark}
                  onChange={(e) => setConfig({ ...config, showWatermark: e.target.checked })}
                />
                <span>Institutional Diagonal Watermark</span>
              </label>
            </div>
          </div>

          <div
            className="rounded-xl p-3.5 border text-xs space-y-1"
            style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText, borderColor: colors.border }}
          >
            <div className="flex items-center gap-1.5 font-bold">
              <CheckSquare className="w-4 h-4" />
              <span>Single-Page A4 OMR Geometry Locked</span>
            </div>
            <p className="opacity-90 leading-relaxed">
              Configured for <strong>{Math.min(200, currentPaper.questions.length)}</strong> questions (
              {config.omrOptionCount} options) with 4 corner fiducial anchors and offline QR grading key.
            </p>
          </div>
        </div>

        {/* Right Live Preview Frame */}
        <div className="lg:col-span-8 min-w-0">
          <div
            className="rounded-xl p-2.5 sm:p-4 border flex flex-col items-center justify-center min-h-[380px]"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <div className="w-full max-w-[790px] bg-white rounded-lg shadow-md overflow-hidden border" style={{ borderColor: colors.border }}>
              <iframe
                ref={iframeRef}
                srcDoc={generatedHtml}
                title="A4 Print & OMR Preview"
                className="w-full h-[60dvh] min-h-[380px] max-h-[780px] border-0 bg-white"
              />
            </div>
            <div className="text-xs mt-2 text-center" style={{ color: colors.muted }}>
              Live ISO A4 Portrait Render • Select Print / Save PDF for crisp vector sheets
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PrintGeneratorTab;
