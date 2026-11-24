import React from 'react';
import { useTheme } from '../theme/ThemeProvider';
import { DualMatchParts } from '../utils/questionFormatter';
import { LatexText } from './LatexText';

interface DualMatchCardProps {
  data: DualMatchParts;
}

export const DualMatchCard: React.FC<DualMatchCardProps> = ({ data }) => {
  const { colors, scalePx } = useTheme();

  return (
    <div
      className="my-2 rounded-xl overflow-hidden"
      style={{
        border: `1px solid ${colors.border}`,
        backgroundColor: colors.surface,
      }}
    >
      <div
        className="grid grid-cols-2 text-xs font-bold"
        style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
      >
        <div className="px-3 py-2 border-r" style={{ borderColor: 'rgba(255,255,255,0.18)' }}>
          <LatexText text={data.leftHeader} defaultBold />
        </div>
        <div className="px-3 py-2">
          <LatexText text={data.rightHeader} defaultBold />
        </div>
      </div>

      <div>
        {data.rows.map((row, idx) => (
          <div
            key={idx}
            className="grid grid-cols-2"
            style={{
              fontSize: scalePx(13),
              backgroundColor: idx % 2 === 1 ? colors.surfaceVariant : colors.surface,
              borderTop: `1px solid ${colors.borderLight}`,
            }}
          >
            <div
              className="px-3 py-2 flex items-start gap-2 border-r"
              style={{ borderColor: colors.borderLight, color: colors.ink }}
            >
              {row.leftKey && (
                <span
                  className="px-1.5 py-0.5 rounded text-[0.6875rem] font-bold flex-shrink-0"
                  style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                >
                  {row.leftKey}
                </span>
              )}
              <span className="flex-1">
                <LatexText text={row.leftText} />
              </span>
            </div>
            <div className="px-3 py-2 flex items-start gap-2" style={{ color: colors.ink }}>
              {row.rightKey && (
                <span
                  className="px-1.5 py-0.5 rounded text-[0.6875rem] font-bold flex-shrink-0"
                  style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}
                >
                  {row.rightKey}
                </span>
              )}
              <span className="flex-1">
                <LatexText text={row.rightText} />
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default DualMatchCard;
