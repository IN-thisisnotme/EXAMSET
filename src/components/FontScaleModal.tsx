import React from 'react';
import { Type, Check, X } from 'lucide-react';
import { FONT_SCALE_OPTIONS } from '../theme/colors';
import { useTheme } from '../theme/ThemeProvider';

interface FontScaleModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const FontScaleModal: React.FC<FontScaleModalProps> = ({ isOpen, onClose }) => {
  const { fontScaleMode, setFontScaleMode, colors, scalePx } = useTheme();

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.55)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-2xl shadow-2xl overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 flex items-center justify-between"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="w-8 h-8 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <Type className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Reading Typography Size
              </h3>
              <p className="text-[11px]" style={{ color: colors.muted }}>
                Scales question stems, options, and explanations globally
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg"
            style={{ color: colors.muted }}
            aria-label="Close font size modal"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <div className="space-y-2">
            {FONT_SCALE_OPTIONS.map((opt) => {
              const selected = fontScaleMode === opt.mode;
              return (
                <button
                  key={opt.mode}
                  type="button"
                  onClick={() => setFontScaleMode(opt.mode)}
                  className="w-full flex items-center justify-between p-3 rounded-xl text-left transition"
                  style={{
                    backgroundColor: selected ? colors.tealLight : colors.surfaceVariant,
                    border: `1.5px solid ${selected ? colors.teal : colors.border}`,
                  }}
                >
                  <div>
                    <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                      {opt.label}
                    </div>
                    <div className="text-[11px]" style={{ color: colors.muted }}>
                      {opt.description}
                    </div>
                  </div>
                  {selected && (
                    <div
                      className="w-6 h-6 rounded-full flex items-center justify-center text-white"
                      style={{ backgroundColor: colors.teal }}
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
            className="p-3.5 rounded-xl space-y-1"
            style={{
              backgroundColor: colors.blueLight,
              border: `1px solid ${colors.border}`,
            }}
          >
            <div className="text-[10px] font-bold uppercase tracking-wider" style={{ color: colors.teal }}>
              Live Stem Preview
            </div>
            <p className="font-semibold leading-snug" style={{ fontSize: scalePx(14), color: colors.inkBold }}>
              Q1. Which atmospheric gas contributes most strongly to the natural greenhouse effect?
            </p>
            <p style={{ fontSize: scalePx(12.5), color: colors.ink }}>
              (A) Water Vapor (H₂O) &nbsp;&nbsp; (B) Carbon Dioxide (CO₂)
            </p>
          </div>
        </div>

        <div
          className="px-5 py-3 flex justify-end"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-xs font-bold"
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
