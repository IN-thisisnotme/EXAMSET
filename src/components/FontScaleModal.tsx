import React from 'react';
import { Type, Check, X, MonitorSmartphone } from 'lucide-react';
import { FONT_SCALE_OPTIONS } from '../theme/colors';
import { useTheme } from '../theme/ThemeProvider';

interface FontScaleModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const FontScaleModal: React.FC<FontScaleModalProps> = ({ isOpen, onClose }) => {
  const {
    fontScaleMode,
    setFontScaleMode,
    fontScale,
    browserBaselinePx,
    effectiveRootPx,
    colors,
    scalePx,
  } = useTheme();

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.55)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md max-h-[90dvh] flex flex-col rounded-2xl shadow-2xl overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-4 sm:px-5 py-3.5 flex items-center justify-between flex-shrink-0"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <Type className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold truncate" style={{ color: colors.inkBold }}>
                Reading Typography &amp; System Scale
              </h3>
              <p className="text-[0.6875rem] truncate" style={{ color: colors.muted }}>
                Coordinated across system, browser, and question cards
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-lg flex-shrink-0"
            style={{ color: colors.muted }}
            aria-label="Close font size modal"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 sm:p-5 space-y-4 overflow-y-auto flex-1">
          {/* Active Resolution Diagnostic Bar */}
          <div
            className="px-3 py-2 rounded-xl border flex flex-wrap items-center justify-between gap-2 text-[0.6875rem]"
            style={{
              backgroundColor: colors.surfaceVariant,
              borderColor: colors.border,
              color: colors.ink,
            }}
          >
            <span className="flex items-center gap-1.5 font-semibold">
              <MonitorSmartphone className="w-3.5 h-3.5" style={{ color: colors.teal }} />
              Browser Base: <strong>{browserBaselinePx}px</strong>
            </span>
            <span className="font-mono font-bold" style={{ color: colors.tealDark }}>
              Active Root: {effectiveRootPx}px ({Math.round(fontScale * 100)}%)
            </span>
          </div>

          <div className="space-y-2">
            {FONT_SCALE_OPTIONS.map((opt) => {
              const selected = fontScaleMode === opt.mode;
              return (
                <button
                  key={opt.mode}
                  type="button"
                  onClick={() => setFontScaleMode(opt.mode)}
                  className="w-full min-h-[48px] flex items-center justify-between gap-3 p-3 rounded-xl text-left transition"
                  style={{
                    backgroundColor: selected ? colors.tealLight : colors.surfaceVariant,
                    border: `1.5px solid ${selected ? colors.teal : colors.border}`,
                  }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                      {opt.label}
                    </div>
                    <div className="text-[0.6875rem] leading-snug" style={{ color: colors.muted }}>
                      {opt.description}
                    </div>
                  </div>
                  {selected && (
                    <div
                      className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0"
                      style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                    >
                      <Check className="w-3.5 h-3.5" />
                    </div>
                  )}
                </button>
              );
            })}
          </div>

          {/* Live Preview */}
          <div
            className="p-3.5 rounded-xl space-y-1.5"
            style={{
              backgroundColor: colors.blueLight,
              border: `1px solid ${colors.border}`,
            }}
          >
            <div className="text-[0.625rem] font-bold uppercase tracking-wider" style={{ color: colors.teal }}>
              Live Stem &amp; Formula Preview
            </div>
            <p className="font-semibold leading-snug" style={{ fontSize: scalePx(14), color: colors.inkBold }}>
              Q1. Which atmospheric gas contributes most strongly to the natural greenhouse effect?
            </p>
            <p style={{ fontSize: scalePx(12.5), color: colors.ink }}>
              (A) Water Vapor (H₂O) &nbsp;&nbsp; (B) Carbon Dioxide (CO₂)
            </p>
            <p className="font-mono text-[0.6875rem]" style={{ color: colors.muted }}>
              Formula density: E = mc² • pH = -log₁₀[H⁺]
            </p>
          </div>
        </div>

        <div
          className="px-4 sm:px-5 py-3 flex items-center justify-between gap-2 flex-shrink-0"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <span className="text-[0.6875rem]" style={{ color: colors.muted }}>
            Browser zoom stays active in all modes
          </span>
          <button
            type="button"
            onClick={onClose}
            className="min-h-[44px] px-4 py-2 rounded-lg text-xs font-bold flex-shrink-0"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Apply Typography
          </button>
        </div>
      </div>
    </div>
  );
};

export default FontScaleModal;
