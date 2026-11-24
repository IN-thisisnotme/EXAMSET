import React from 'react';
import { Palette, Sun, Moon, Monitor, Check, X } from 'lucide-react';
import { PRESET_METADATA, ThemeMode } from '../theme/colors';
import { useTheme } from '../theme/ThemeProvider';

interface ThemePaletteModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ThemePaletteModal: React.FC<ThemePaletteModalProps> = ({ isOpen, onClose }) => {
  const { themeMode, setThemeMode, preset, setPreset, colors, isDark } = useTheme();

  if (!isOpen) return null;

  const modes: { id: ThemeMode; label: string; icon: React.ReactNode }[] = [
    { id: 'LIGHT', label: 'Light', icon: <Sun className="w-4 h-4" /> },
    { id: 'DARK', label: 'Dark', icon: <Moon className="w-4 h-4" /> },
    { id: 'SYSTEM', label: 'System', icon: <Monitor className="w-4 h-4" /> },
  ];

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
              <Palette className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-sm font-bold truncate" style={{ color: colors.inkBold }}>
                Appearance &amp; Theme Palette
              </h3>
              <p className="text-[11px] truncate" style={{ color: colors.muted }}>
                Curated palettes tuned for long study &amp; print clarity
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-lg transition flex-shrink-0"
            style={{ color: colors.muted }}
            aria-label="Close theme selector"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 sm:p-5 space-y-5 overflow-y-auto flex-1">
          {/* Brightness Mode */}
          <div className="space-y-2">
            <label className="text-[11px] font-bold uppercase tracking-wider" style={{ color: colors.muted }}>
              Display Mode ({isDark ? 'Dark Active' : 'Light Active'})
            </label>
            <div className="grid grid-cols-3 gap-2">
              {modes.map((m) => {
                const active = themeMode === m.id;
                return (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setThemeMode(m.id)}
                    className="flex items-center justify-center gap-1.5 py-2.5 px-3 min-h-[44px] rounded-xl text-xs font-bold transition"
                    style={{
                      backgroundColor: active ? colors.teal : colors.surfaceVariant,
                      color: active ? '#FFFFFF' : colors.ink,
                      border: `1px solid ${active ? colors.teal : colors.border}`,
                    }}
                  >
                    {m.icon}
                    <span>{m.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Preset List */}
          <div className="space-y-2">
            <label className="text-[11px] font-bold uppercase tracking-wider" style={{ color: colors.muted }}>
              Color Scheme Preset
            </label>
            <div className="space-y-2">
              {PRESET_METADATA.map((item) => {
                const selected = preset === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setPreset(item.id)}
                    className="w-full flex items-center justify-between gap-2 p-3 min-h-[44px] rounded-xl text-left transition"
                    style={{
                      backgroundColor: selected ? colors.tealLight : colors.surfaceVariant,
                      border: `1.5px solid ${selected ? colors.teal : colors.border}`,
                    }}
                  >
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      <div className="flex -space-x-1.5 shrink-0">
                        {item.swatches.map((hex, i) => (
                          <span
                            key={i}
                            className="w-5 h-5 rounded-full border"
                            style={{ backgroundColor: hex, borderColor: colors.border }}
                          />
                        ))}
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                          {item.name}
                        </div>
                        <div className="text-[11px] leading-snug" style={{ color: colors.muted }}>
                          {item.subtitle}
                        </div>
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
          </div>
        </div>

        <div
          className="px-5 py-3 flex justify-end"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 min-h-[44px] rounded-lg text-xs font-bold text-white"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

export default ThemePaletteModal;
