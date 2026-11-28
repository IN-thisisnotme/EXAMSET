import React, { useState } from 'react';
import { Award, Clock, Download, Eye, History, Trash2, X } from 'lucide-react';
import { AttemptRecord, QuestionPaper } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { AnswerKeyMatcher } from '../utils/answerKeyMatcher';
import { PsychometricEngine } from '../utils/psychometricEngine';
import { downloadTextSafely } from '../utils/downloadHelper';

interface AttemptHistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  paper: QuestionPaper;
  attempts: AttemptRecord[];
  onDeleteAttempt: (attemptId: string) => void;
}

export const AttemptHistoryModal: React.FC<AttemptHistoryModalProps> = ({
  isOpen,
  onClose,
  paper,
  attempts,
  onDeleteAttempt,
}) => {
  const { colors } = useTheme();
  const [selectedAttemptId, setSelectedAttemptId] = useState<string | null>(
    attempts[0]?.id || null
  );

  if (!isOpen) return null;

  const selectedAttempt = attempts.find((a) => a.id === selectedAttemptId) || attempts[0] || null;

  const formatDuration = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}m ${s}s`;
  };

  const handleDownloadReportCard = (att: AttemptRecord, rankIdx: number) => {
    const text = PsychometricEngine.generateStudentReportCard(
      {
        rank: rankIdx + 1,
        rollNumber: att.rollNumber,
        bookletNumber: att.bookletNumber,
        bookletCode: att.bookletCode,
        totalScore: att.score,
        correctCount: att.correctCount,
        partialCount: att.partialCount,
        incorrectCount: att.incorrectCount,
        unansweredCount: att.unansweredCount,
        accuracyPercent: att.accuracyPercent,
        submittedAt: att.submittedAt,
      },
      paper,
      att.submittedAnswers,
      attempts.length
    );
    downloadTextSafely(
      text,
      `ReportCard_${att.rollNumber}_${paper.metadata.title.replace(/\s+/g, '_')}.txt`,
      'text/plain;charset=utf-8'
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 backdrop-blur-sm"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
    >
      <div
        className="w-full max-w-4xl max-h-[88vh] rounded-2xl shadow-2xl border flex flex-col overflow-hidden"
        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
      >
        <div
          className="px-5 py-4 border-b flex items-center justify-between"
          style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2">
            <History className="w-5 h-5" />
            <div>
              <h3 className="text-base font-bold">Saved Attempt History ({attempts.length})</h3>
              <p className="text-[0.6875rem] opacity-85">{paper.metadata.title}</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:opacity-80" aria-label="Close history">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5">
          {attempts.length === 0 ? (
            <div className="text-center py-12 space-y-2">
              <Award className="w-10 h-10 mx-auto" style={{ color: colors.muted }} />
              <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
                No Submitted Attempts Yet
              </p>
              <p className="text-xs" style={{ color: colors.muted }}>
                Complete a test in the Student Portal to record candidate scorecards and pacing telemetry.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
              {/* Left List */}
              <div className="md:col-span-5 space-y-2 max-h-[60vh] overflow-y-auto pr-1">
                {attempts.map((att, idx) => {
                  const isSel = selectedAttempt?.id === att.id;
                  return (
                    <div
                      key={att.id}
                      onClick={() => setSelectedAttemptId(att.id)}
                      className="p-3 rounded-xl border cursor-pointer transition space-y-1.5"
                      style={{
                        backgroundColor: isSel ? colors.tealLight : colors.surfaceVariant,
                        borderColor: isSel ? colors.teal : colors.border,
                      }}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-bold" style={{ color: colors.inkBold }}>
                          {att.score.toFixed(2)} / {att.maxScore.toFixed(2)} marks ({att.accuracyPercent}%)
                        </span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onDeleteAttempt(att.id);
                          }}
                          className="p-1 rounded hover:opacity-80"
                          style={{ color: colors.red }}
                          title="Delete attempt record"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                      <div className="flex items-center justify-between text-[0.6875rem]" style={{ color: colors.muted }}>
                        <span>Roll: {att.rollNumber}</span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {formatDuration(att.timeSpentSeconds)}
                        </span>
                      </div>
                      <div className="text-[0.625rem]" style={{ color: colors.inkMuted }}>
                        C: {att.correctCount} • P: {att.partialCount} • W: {att.incorrectCount} • S: {att.unansweredCount} •{' '}
                        {new Date(att.submittedAt).toLocaleString()}
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDownloadReportCard(att, idx);
                        }}
                        className="inline-flex items-center gap-1 text-[0.625rem] font-bold pt-1"
                        style={{ color: colors.teal }}
                      >
                        <Download className="w-3 h-3" />
                        Export Report Card (.txt)
                      </button>
                    </div>
                  );
                })}
              </div>

              {/* Right Audit Detail */}
              {selectedAttempt && (
                <div
                  className="md:col-span-7 border rounded-xl p-4 space-y-3 max-h-[60vh] overflow-y-auto"
                  style={{ borderColor: colors.border, backgroundColor: colors.surfaceVariant }}
                >
                  <div className="flex items-center justify-between border-b pb-2" style={{ borderColor: colors.border }}>
                    <div className="text-xs font-bold flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                      <Eye className="w-4 h-4" style={{ color: colors.teal }} />
                      Question-by-Question Audit
                    </div>
                    <span className="text-[0.6875rem] font-mono" style={{ color: colors.muted }}>
                      +{selectedAttempt.positiveMarks} / -{selectedAttempt.negativeMarks}
                    </span>
                  </div>

                  <div className="space-y-1.5">
                    {paper.questions.map((q) => {
                      const ans = selectedAttempt.submittedAnswers[q.num];
                      const secs = selectedAttempt.questionTimes[q.num] ?? 0;
                      const grade = AnswerKeyMatcher.scoreQuestion(
                        q,
                        ans,
                        selectedAttempt.positiveMarks,
                        selectedAttempt.negativeMarks,
                        selectedAttempt.unattendedMarks,
                        selectedAttempt.msqScoringPolicy
                      );

                      const badgeColors =
                        grade.status === 'CORRECT' || grade.status === 'NEUTRALIZED'
                          ? { bg: colors.badgeSuccessBg, fg: colors.badgeSuccessText }
                          : grade.status === 'PARTIAL' || grade.status === 'UNANSWERED'
                          ? { bg: colors.badgeWarningBg, fg: colors.badgeWarningText }
                          : { bg: colors.badgeDangerBg, fg: colors.badgeDangerText };

                      return (
                        <div
                          key={q.id}
                          className="px-3 py-2 rounded-lg border flex items-center justify-between text-xs"
                          style={{ backgroundColor: colors.surface, borderColor: colors.borderLight }}
                        >
                          <div className="flex items-center gap-2">
                            <span className="font-bold">Q.{q.num}</span>
                            <span className="font-mono" style={{ color: colors.muted }}>
                              Chosen: <strong>{AnswerKeyMatcher.formatKeyForDisplay(ans)}</strong>
                            </span>
                            <span className="font-mono" style={{ color: colors.teal }}>
                              Key: <strong>{AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer)}</strong>
                            </span>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-[0.625rem]" style={{ color: colors.muted }}>
                              {secs}s
                            </span>
                            <span
                              className="px-2 py-0.5 rounded-full text-[0.625rem] font-bold"
                              style={{ backgroundColor: badgeColors.bg, color: badgeColors.fg }}
                            >
                              {grade.status} ({grade.marks >= 0 ? `+${grade.marks.toFixed(1)}` : grade.marks.toFixed(1)})
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AttemptHistoryModal;
