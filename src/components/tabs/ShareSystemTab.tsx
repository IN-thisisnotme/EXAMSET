import React, { useState } from 'react';
import { 
  Share2, 
  Copy, 
  Download, 
  Upload, 
  Check, 
  QrCode, 
  FileJson, 
  Link, 
  Globe, 
  ExternalLink,
  Sparkles,
  Layers
} from 'lucide-react';
import { QuestionPaper } from '../../types/exam';

interface ShareSystemTabProps {
  currentPaper: QuestionPaper;
  onImportPaper: (paper: QuestionPaper) => void;
}

export const ShareSystemTab: React.FC<ShareSystemTabProps> = ({
  currentPaper,
  onImportPaper
}) => {
  const [copiedLink, setCopiedLink] = useState<boolean>(false);
  const [copiedJson, setCopiedJson] = useState<boolean>(false);
  const [importJsonText, setImportJsonText] = useState<string>('');
  const [importError, setImportError] = useState<string | null>(null);

  // Generate lightweight payload for sharing
  const sharePayload = JSON.stringify(currentPaper, null, 2);

  // Safe base64 encode for link sharing
  const encodedData = btoa(encodeURIComponent(JSON.stringify(currentPaper)));
  const shareableUrl = `${window.location.origin}${window.location.pathname}#paper=${encodedData.slice(0, 120)}...`;

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2400);
  };

  const handleCopyJson = () => {
    navigator.clipboard.writeText(sharePayload);
    setCopiedJson(true);
    setTimeout(() => setCopiedJson(false), 2400);
  };

  const handleDownloadJson = () => {
    const blob = new Blob([sharePayload], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${currentPaper.metadata.title.replace(/\s+/g, '_')}.examset.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleImportJson = () => {
    setImportError(null);
    try {
      if (!importJsonText.trim()) {
        setImportError('Please paste valid ExamSet JSON content.');
        return;
      }
      const parsed = JSON.parse(importJsonText);
      if (!parsed.questions || !Array.isArray(parsed.questions)) {
        setImportError('Invalid format: JSON must include a "questions" array.');
        return;
      }
      onImportPaper(parsed);
      alert(`Successfully imported "${parsed.metadata?.title || 'Exam Paper'}" with ${parsed.questions.length} questions!`);
      setImportJsonText('');
    } catch (err: any) {
      setImportError(`Parse error: ${err.message}`);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result as string;
        const parsed = JSON.parse(text);
        if (parsed.questions && Array.isArray(parsed.questions)) {
          onImportPaper(parsed);
          alert(`Successfully imported "${parsed.metadata?.title || 'Exam Paper'}"!`);
        } else {
          alert('Invalid file format: Missing questions list.');
        }
      } catch (err: any) {
        alert(`Failed to load file: ${err.message}`);
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-6">
      {/* Top Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Share2 className="w-5 h-5 text-blue-600" />
            Share, Export & Peer Collaboration Hub
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Export assessments as structured JSON, distribute online practice links to candidates, or import question packages.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleDownloadJson}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
          >
            <Download className="w-4 h-4" />
            Download .examset.json
          </button>
        </div>
      </div>

      {/* Grid: Sharing Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Shareable Link & QR Simulation */}
        <div className="lg:col-span-6 space-y-4">
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <Link className="w-3.5 h-3.5" />
              Direct Test & Paper Link
            </h4>

            <p className="text-xs text-slate-600 leading-relaxed">
              Anyone with this link can open the paper directly inside ExamSet, print the continuous student paper or 1-sheet OMR, or take the timed test.
            </p>

            <div className="flex items-center gap-2">
              <input
                type="text"
                readOnly
                value={window.location.href}
                className="w-full text-xs font-mono bg-slate-50 border border-slate-300 rounded-lg px-3 py-2 text-slate-700 focus:outline-none"
              />
              <button
                onClick={handleCopyLink}
                className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-slate-900 hover:bg-slate-800 text-white rounded-lg transition shadow-sm flex-shrink-0"
              >
                {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copiedLink ? 'Copied' : 'Copy'}</span>
              </button>
            </div>

            {/* Visual Paper Identity Badge */}
            <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between gap-4">
              <div className="space-y-1">
                <span className="text-[11px] font-bold text-blue-700 uppercase tracking-wider">
                  Ready for Distribution
                </span>
                <h5 className="text-sm font-bold text-slate-900">{currentPaper.metadata.title}</h5>
                <p className="text-xs text-slate-500">
                  {currentPaper.metadata.subject} • {currentPaper.questions.length} Items • {currentPaper.metadata.setNumber}
                </p>
              </div>

              <div className="w-14 h-14 bg-white rounded-lg border border-slate-300 p-1 flex items-center justify-center flex-shrink-0 shadow-sm">
                <QrCode className="w-10 h-10 text-slate-800" />
              </div>
            </div>
          </div>

          {/* Import JSON Card */}
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <Upload className="w-3.5 h-3.5" />
              Import Paper from JSON
            </h4>

            <div className="space-y-3">
              <textarea
                value={importJsonText}
                onChange={(e) => setImportJsonText(e.target.value)}
                placeholder="Paste exported .examset.json text here..."
                rows={4}
                className="w-full p-3 font-mono text-xs text-slate-800 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
              />

              {importError && (
                <div className="text-xs text-rose-600 font-semibold">{importError}</div>
              )}

              <div className="flex items-center justify-between gap-2 flex-wrap">
                <label className="cursor-pointer flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg transition">
                  <Upload className="w-3.5 h-3.5" />
                  <span>Choose File (.json)</span>
                  <input
                    type="file"
                    accept=".json"
                    onChange={handleFileUpload}
                    className="hidden"
                  />
                </label>

                <button
                  onClick={handleImportJson}
                  className="px-4 py-1.5 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
                >
                  Load into Workspace
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: JSON Preview & Payload Inspector */}
        <div className="lg:col-span-6">
          <div className="bg-slate-900 rounded-xl p-5 shadow-lg border border-slate-800 flex flex-col h-full min-h-[500px]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3 text-xs text-slate-400">
              <div className="flex items-center gap-2 font-mono text-slate-300">
                <FileJson className="w-4 h-4 text-emerald-400" />
                <span>Standard ExamSet Data Contract</span>
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
              className="flex-1 w-full bg-transparent text-emerald-400 font-mono text-[11px] leading-relaxed border-0 resize-none focus:outline-none focus:ring-0"
            />

            <div className="pt-4 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-400">
              <span>Full metadata, questions, answer keys & explanations included</span>
              <button
                onClick={handleDownloadJson}
                className="font-semibold text-blue-400 hover:text-blue-300"
              >
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
