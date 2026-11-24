import React from 'react';
import { useTheme } from '../theme/ThemeProvider';
import { MarkdownTableData } from '../utils/questionFormatter';
import { LatexText } from './LatexText';

interface MarkdownTableCardProps {
  table: MarkdownTableData;
}

export const MarkdownTableCard: React.FC<MarkdownTableCardProps> = ({ table }) => {
  const { colors, scalePx } = useTheme();

  if (!table.headers.length) return null;

  return (
    <div
      className="my-2 rounded-xl overflow-x-auto"
      style={{
        border: `1px solid ${colors.border}`,
        backgroundColor: colors.surface,
      }}
    >
      <table className="w-full text-left border-collapse" style={{ fontSize: scalePx(12.5) }}>
        <thead>
          <tr style={{ backgroundColor: colors.tealLight }}>
            {table.headers.map((h, idx) => (
              <th
                key={idx}
                className="px-3 py-2 font-bold"
                style={{
                  color: colors.inkBold,
                  borderBottom: `1px solid ${colors.border}`,
                  borderRight: idx < table.headers.length - 1 ? `1px solid ${colors.borderLight}` : undefined,
                }}
              >
                <LatexText text={h} defaultBold />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row, rIdx) => (
            <tr
              key={rIdx}
              style={{
                backgroundColor: rIdx % 2 === 1 ? colors.surfaceVariant : colors.surface,
              }}
            >
              {row.map((cell, cIdx) => (
                <td
                  key={cIdx}
                  className="px-3 py-2 align-top"
                  style={{
                    color: colors.ink,
                    borderBottom: rIdx < table.rows.length - 1 ? `1px solid ${colors.borderLight}` : undefined,
                    borderRight: cIdx < row.length - 1 ? `1px solid ${colors.borderLight}` : undefined,
                  }}
                >
                  <LatexText text={cell} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default MarkdownTableCard;
