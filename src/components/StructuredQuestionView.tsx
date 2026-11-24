import React from 'react';
import { Calculator, Image as ImageIcon, ShieldCheck, AlertTriangle } from 'lucide-react';
import {
  extractMarkdownTables,
  parseAssertionReason,
  parseDualMatchData,
  parseStatementQuestion,
} from '../utils/questionFormatter';
import { useTheme } from '../theme/ThemeProvider';
import { LatexText } from './LatexText';
import { MarkdownTableCard } from './MarkdownTableCard';
import { DualMatchCard } from './DualMatchCard';

interface StructuredQuestionViewProps {
  questionText: string;
  formula?: string | null;
  diagramLabel?: string | null;
  diagramUrl?: string | null;
  isVerified?: boolean;
  isNeutralized?: boolean;
  className?: string;
}

export const StructuredQuestionView: React.FC<StructuredQuestionViewProps> = ({
  questionText,
  formula,
  diagramLabel,
  diagramUrl,
  isVerified,
  isNeutralized,
  className = '',
}) => {
  const { colors, scalePx } = useTheme();
  const arParts = parseAssertionReason(questionText);
  const dualMatch = !arParts ? parseDualMatchData(questionText) : null;
  const stParts = !arParts && !dualMatch ? parseStatementQuestion(questionText) : null;
  const tableData =
    !arParts && !dualMatch ? extractMarkdownTables(stParts ? '' : questionText) : { textWithoutTables: questionText, tables: [] };

  const renderExtras = () => (
    <>
      {formula && formula.trim().length > 0 && (
        <div
          className="mt-2.5 p-2.5 rounded-lg flex items-start gap-2"
          style={{
            backgroundColor: colors.tealLight,
            border: `1px solid ${colors.border}`,
          }}
        >
          <Calculator className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: colors.teal }} />
          <div className="min-w-0 flex-1">
            <div className="text-[10px] font-bold uppercase tracking-wider" style={{ color: colors.tealDark }}>
              Reference Formula
            </div>
            <div className="text-xs font-mono font-semibold mt-0.5" style={{ color: colors.inkBold }}>
              <LatexText text={formula} />
            </div>
          </div>
        </div>
      )}

      {(diagramUrl || diagramLabel) && (
        <div
          className="mt-2.5 p-2.5 rounded-lg space-y-2"
          style={{
            backgroundColor: colors.surfaceVariant,
            border: `1px solid ${colors.border}`,
          }}
        >
          <div className="flex items-center gap-1.5 text-[11px] font-bold" style={{ color: colors.inkBold }}>
            <ImageIcon className="w-3.5 h-3.5" style={{ color: colors.teal }} />
            <span>{diagramLabel || 'Attached Figure / Diagram'}</span>
          </div>
          {diagramUrl && (
            <img
              src={diagramUrl}
              alt={diagramLabel || 'Question diagram'}
              className="max-h-56 rounded border object-contain"
              style={{ borderColor: colors.border, backgroundColor: '#FFFFFF' }}
            />
          )}
        </div>
      )}

      {(isVerified || isNeutralized) && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {isVerified && (
            <span
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold"
              style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
            >
              <ShieldCheck className="w-3 h-3" />
              Verified Seal
            </span>
          )}
          {isNeutralized && (
            <span
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold"
              style={{ backgroundColor: colors.badgeWarningBg, color: colors.badgeWarningText }}
            >
              <AlertTriangle className="w-3 h-3" />
              Neutralized (Bonus Full Marks)
            </span>
          )}
        </div>
      )}
    </>
  );

  if (arParts) {
    return (
      <div className={`space-y-2.5 ${className}`} style={{ fontSize: scalePx(14) }}>
        {arParts.leadIn && (
          <div className="italic text-xs" style={{ color: colors.muted }}>
            <LatexText text={arParts.leadIn} />
          </div>
        )}

        <div
          className="p-3 rounded-xl space-y-2"
          style={{
            backgroundColor: colors.blueLight,
            border: `1px solid ${colors.border}`,
          }}
        >
          <div className="flex items-start gap-2.5">
            <span
              className="px-2 py-0.5 rounded text-[11px] font-bold flex-shrink-0 uppercase tracking-wide"
              style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
            >
              Assertion (A)
            </span>
            <div className="font-medium leading-relaxed" style={{ color: colors.inkBold }}>
              <LatexText text={arParts.assertion} />
            </div>
          </div>

          <div className="pt-2 flex items-start gap-2.5" style={{ borderTop: `1px solid ${colors.borderLight}` }}>
            <span
              className="px-2 py-0.5 rounded text-[11px] font-bold flex-shrink-0 uppercase tracking-wide"
              style={{ backgroundColor: colors.teal, color: '#FFFFFF' }}
            >
              Reason (R)
            </span>
            <div className="font-medium leading-relaxed" style={{ color: colors.ink }}>
              <LatexText text={arParts.reason} />
            </div>
          </div>
        </div>

        {arParts.leadOut && (
          <div className="italic font-medium text-xs" style={{ color: colors.muted }}>
            <LatexText text={arParts.leadOut} />
          </div>
        )}
        {renderExtras()}
      </div>
    );
  }

  if (dualMatch) {
    return (
      <div className={`space-y-2 ${className}`} style={{ fontSize: scalePx(14) }}>
        {dualMatch.leadIn && (
          <div className="font-semibold leading-snug" style={{ color: colors.inkBold }}>
            <LatexText text={dualMatch.leadIn} />
          </div>
        )}
        <DualMatchCard data={dualMatch} />
        {dualMatch.leadOut && (
          <div className="text-xs italic" style={{ color: colors.muted }}>
            <LatexText text={dualMatch.leadOut} />
          </div>
        )}
        {renderExtras()}
      </div>
    );
  }

  if (stParts) {
    return (
      <div className={`space-y-2 ${className}`} style={{ fontSize: scalePx(14) }}>
        {stParts.leadIn && (
          <div className="font-semibold leading-snug" style={{ color: colors.inkBold }}>
            <LatexText text={stParts.leadIn} />
          </div>
        )}

        <div
          className="p-3 rounded-xl space-y-2"
          style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
        >
          {stParts.statements.map((st, idx) => (
            <div key={idx} className="flex items-start gap-2">
              <span
                className="px-1.5 py-0.5 rounded text-[11px] font-bold flex-shrink-0"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
              >
                {st.label}
              </span>
              <div className="leading-snug" style={{ color: colors.ink }}>
                <LatexText text={st.text} />
              </div>
            </div>
          ))}
        </div>

        {stParts.leadOut && (
          <div className="font-medium leading-snug" style={{ color: colors.inkBold }}>
            <LatexText text={stParts.leadOut} />
          </div>
        )}
        {renderExtras()}
      </div>
    );
  }

  return (
    <div className={`leading-snug ${className}`} style={{ fontSize: scalePx(14), color: colors.inkBold }}>
      {tableData.tables.length > 0 ? (
        <>
          {tableData.textWithoutTables && (
            <div className="font-semibold mb-1">
              <LatexText text={tableData.textWithoutTables} />
            </div>
          )}
          {tableData.tables.map((tbl, idx) => (
            <MarkdownTableCard key={idx} table={tbl} />
          ))}
        </>
      ) : (
        <div className="font-semibold">
          <LatexText text={questionText} />
        </div>
      )}
      {renderExtras()}
    </div>
  );
};

export default StructuredQuestionView;
