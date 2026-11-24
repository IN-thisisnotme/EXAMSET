import React from 'react';
import { useTheme } from '../theme/ThemeProvider';
import { LatexHelper } from '../utils/latexHelper';
import { RichTextHelper } from '../utils/richTextHelper';

interface LatexTextProps {
  text: string;
  className?: string;
  style?: React.CSSProperties;
  defaultBold?: boolean;
}

const renderRichTokens = (raw: string, defaultBold: boolean, codeBg: string, keyPrefix: string) => {
  const formatted = LatexHelper.formatMathExpression(raw);
  const tokens = RichTextHelper.tokenize(formatted, defaultBold);

  return tokens.map((tok, i) => {
    const key = `${keyPrefix}-${i}`;
    let content: React.ReactNode = tok.text;

    if (tok.code) {
      content = (
        <code
          key={key}
          className="px-1 py-0.5 rounded font-mono text-[0.9em]"
          style={{ backgroundColor: codeBg }}
        >
          {content}
        </code>
      );
    }
    if (tok.sub) {
      content = <sub key={key} className="text-[0.75em]">{content}</sub>;
    } else if (tok.sup) {
      content = <sup key={key} className="text-[0.75em]">{content}</sup>;
    }

    if (tok.bold && tok.italic) {
      return <strong key={key}><em>{content}</em></strong>;
    }
    if (tok.bold) {
      return <strong key={key} className="font-bold">{content}</strong>;
    }
    if (tok.italic) {
      return <em key={key}>{content}</em>;
    }
    return <React.Fragment key={key}>{content}</React.Fragment>;
  });
};

export const LatexText: React.FC<LatexTextProps> = ({
  text,
  className = '',
  style,
  defaultBold = false,
}) => {
  const { colors } = useTheme();
  if (!text) return null;

  const segments = LatexHelper.segmentLatex(text);

  return (
    <span className={`whitespace-pre-line break-words ${className}`} style={style}>
      {segments.map((seg, idx) => {
        if (seg.isMath && seg.isDisplay) {
          return (
            <span
              key={`disp-${idx}`}
              className="block my-2 px-3 py-2 rounded-lg font-mono text-[0.95em] text-center overflow-x-auto"
              style={{
                backgroundColor: colors.blueLight,
                border: `1px solid ${colors.border}`,
                color: colors.inkBold,
              }}
            >
              {renderRichTokens(seg.content, defaultBold, colors.surfaceVariant, `dm-${idx}`)}
            </span>
          );
        }
        if (seg.isMath) {
          return (
            <span
              key={`inl-${idx}`}
              className="font-serif italic px-0.5"
              style={{ color: colors.inkBold }}
            >
              {renderRichTokens(seg.content, defaultBold, colors.surfaceVariant, `im-${idx}`)}
            </span>
          );
        }
        return (
          <React.Fragment key={`txt-${idx}`}>
            {renderRichTokens(seg.content, defaultBold, colors.surfaceVariant, `tx-${idx}`)}
          </React.Fragment>
        );
      })}
    </span>
  );
};

export default LatexText;
