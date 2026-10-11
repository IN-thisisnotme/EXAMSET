import React, { useState, useMemo, useRef } from 'react';
import { 
  Printer, 
  Settings, 
  Eye, 
  Download, 
  FileCheck, 
  Columns, 
  UserCheck, 
  CheckSquare, 
  Sparkles,
  ExternalLink
} from 'lucide-react';
import { QuestionPaper, ExamConfig, DEFAULT_EXAM_CONFIG, PrintMode } from '../../types/exam';
import { PrintDocumentHelper } from '../../utils/printDocumentHelper';

interface PrintGeneratorTabProps {
  currentPaper: QuestionPaper;
}

export const PrintGeneratorTab: React.FC<PrintGeneratorTabProps> = ({ currentPaper }) => {
  const [config, setConfig] = useState<ExamConfig>(() => {
    return {
      ...DEFAULT_EXAM_CONFIG,
      institutionName: 'CENTRAL BOARD OF SECONDARY EDUCATION',
      examTitle: currentPaper.metadata.title || 'ALL INDIA COMPETITIVE EXAMINATION',
      seriesCode: currentPaper.metadata.setNumber || 'SET - A',
      timeAllowed: '120 Minutes',
      maxMarks: `${currentPaper.questions.length || 100}`
    };
  });

  const [activeMode, setActiveMode] = useState<PrintMode>('STUDENT_PAPER');
  const iframeRef = useRef<HTMLIFrameElement>(null);

  // Generate the formatted HTML for printing and preview
  const generatedHtml = useMemo(() => {
    return PrintDocumentHelper.generatePrintHtml(currentPaper, {
      ...config,
      printMode: activeMode
    });
  }, [currentPaper, config, activeMode]);

  const handlePrint = () => {
    const iframe = iframeRef.current;
    if (iframe && iframe.contentWindow) {
      iframe.contentWindow.focus();
      iframe.contentWindow.print();
    } else {
      // Fallback: Open clean print window
      const printWindow = window.open('', '_blank');
      if (printWindow) {
        printWindow.document.open();
        printWindow.document.write(generatedHtml);
        printWindow.document.close();
        printWindow.focus();
        setTimeout(() => {
          printWindow.print();
        }, 300);
      }
    }
  };

  const handleDownloadHtml = () => {
    const blob = new Blob([generatedHtml], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${currentPaper.metadata.title.replace(/\s+/g, '_')}_${activeMode}.html`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleOpenInNewTab = () => {
    const newWindow = window.open();
    if (newWindow) {
      newWindow.document.open();
      newWindow.document.write(generatedHtml);
      newWindow.document.close();
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header & Mode Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Printer className="w-5 h-5 text-blue-600" />
            A4 Print & Strict Single-Page OMR Generator
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Produces authentic, borderless examination papers and standard 4-column OMR response sheets with zero page-overflow.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleDownloadHtml}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white text-slate-700 border border-slate-300 rounded-lg hover:bg-slate-50 transition shadow-sm"
          >
            <Download className="w-3.5 h-3.5 text-slate-500" />
            Download HTML
          </button>

          <button
            onClick={handleOpenInNewTab}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white text-slate-700 border border-slate-300 rounded-lg hover:bg-slate-50 transition shadow-sm"
          >
            <ExternalLink className="w-3.5 h-3.5 text-slate-500" />
            Full Window
          </button>

          <button
            onClick={handlePrint}
            className="flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition shadow-sm"
          >
            <Printer className="w-4 h-4" />
            Print Document
          </button>
        </div>
      </div>

      {/* Mode Selection Pills */}
      <div className="flex items-center gap-2 border-b border-slate-200 pb-3 overflow-x-auto">
        {[
          { id: 'STUDENT_PAPER', label: 'Student Question Paper', icon: <FileCheck className="w-4 h-4" /> },
          { id: 'OMR_ONLY', label: 'OMR Sheet Only (1 Page A4)', icon: <CheckSquare className="w-4 h-4" /> },
          { id: 'MASTER_BOOKLET', label: 'Master Booklet (With Answers)', icon: <Eye className="w-4 h-4" /> },
          { id: 'SOLUTIONS_ONLY', label: 'Solutions & Answer Key', icon: <Sparkles className="w-4 h-4" /> }
        ].map(mode => (
          <button
            key={mode.id}
            onClick={() => setActiveMode(mode.id as PrintMode)}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition whitespace-nowrap ${
              activeMode === mode.id
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            {mode.icon}
            <span>{mode.label}</span>
          </button>
        ))}
      </div>

      {/* Main Area: Controls & Print Preview */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Side: Layout Configurations */}
        <div className="lg:col-span-4 space-y-4">
          <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <Settings className="w-3.5 h-3.5" />
              Paper & Header Settings
            </h4>

            <div className="space-y-3 text-xs">
              <div>
                <label className="block text-slate-700 font-semibold mb-1">Board / Institution Name</label>
                <input
                  type="text"
                  value={config.institutionName}
                  onChange={(e) => setConfig({ ...config, institutionName: e.target.value })}
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Examination Title</label>
                <input
                  type="text"
                  value={config.examTitle}
                  onChange={(e) => setConfig({ ...config, examTitle: e.target.value })}
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-slate-700 font-semibold mb-1">Series / Set</label>
                  <input
                    type="text"
                    value={config.seriesCode}
                    onChange={(e) => setConfig({ ...config, seriesCode: e.target.value })}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block text-slate-700 font-semibold mb-1">Time Allowed</label>
                  <input
                    type="text"
                    value={config.timeAllowed}
                    onChange={(e) => setConfig({ ...config, timeAllowed: e.target.value })}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Maximum Marks</label>
                <input
                  type="text"
                  value={config.maxMarks}
                  onChange={(e) => setConfig({ ...config, maxMarks: e.target.value })}
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
            </div>

            {/* Toggle Switches */}
            {activeMode === 'STUDENT_PAPER' && (
              <div className="pt-3 border-t border-slate-100 space-y-2.5 text-xs">
                <h5 className="font-bold text-slate-700">Section Toggles</h5>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={config.includeCandidateDetails}
                    onChange={(e) => setConfig({ ...config, includeCandidateDetails: e.target.checked })}
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span className="text-slate-700">Include Candidate Name & Signature Box</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={config.includeInstructions}
                    onChange={(e) => setConfig({ ...config, includeInstructions: e.target.checked })}
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span className="text-slate-700">Include Instructions to Candidates</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={config.twoColumns}
                    onChange={(e) => setConfig({ ...config, twoColumns: e.target.checked })}
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span className="text-slate-700">Two-Column Question Paper Layout</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={config.includeOmrSheet}
                    onChange={(e) => setConfig({ ...config, includeOmrSheet: e.target.checked })}
                    className="rounded text-blue-600 focus:ring-0"
                  />
                  <span className="text-slate-700 font-semibold text-blue-700">Attach 1-Page A4 OMR Sheet at End</span>
                </label>
              </div>
            )}
          </div>

          {/* OMR Strict Guarantee Card */}
          <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-3.5 text-xs text-emerald-900 space-y-1.5">
            <div className="flex items-center gap-1.5 font-bold text-emerald-800">
              <CheckSquare className="w-4 h-4 text-emerald-600" />
              <span>Strict Single-Page A4 OMR Verified</span>
            </div>
            <p className="text-emerald-700 leading-relaxed">
              Standard competitive exam layout dynamically calculated for <strong>{currentPaper.questions.length} questions</strong>.
              Fits seamlessly on 1 sheet of paper at 100% scale without second-page overflow.
            </p>
          </div>
        </div>

        {/* Right Side: Responsive Live A4 Preview Frame */}
        <div className="lg:col-span-8">
          <div className="bg-slate-200 rounded-xl p-4 shadow-inner flex flex-col items-center justify-center min-h-[680px]">
            <div className="w-full max-w-[780px] bg-white rounded-lg shadow-md overflow-hidden border border-slate-300">
              <iframe
                ref={iframeRef}
                srcDoc={generatedHtml}
                title="Print Preview"
                className="w-full h-[720px] border-0 bg-white"
              />
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Previewing A4 Portrait rendering • Standard Print Media Engine
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PrintGeneratorTab;
