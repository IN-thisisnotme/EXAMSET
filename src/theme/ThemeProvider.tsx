import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import {
  AppFontScaleMode,
  ColorSchemePreset,
  ExamColorPalette,
  FONT_SCALE_OPTIONS,
  resolvePalette,
  ThemeMode,
} from './colors';

interface ThemeContextValue {
  themeMode: ThemeMode;
  setThemeMode: (mode: ThemeMode) => void;
  preset: ColorSchemePreset;
  setPreset: (preset: ColorSchemePreset) => void;
  fontScaleMode: AppFontScaleMode;
  setFontScaleMode: (mode: AppFontScaleMode) => void;
  fontScale: number;
  isDark: boolean;
  colors: ExamColorPalette;
  scalePx: (basePx: number) => string;
}

const THEME_MODE_KEY = 'examset_theme_mode_v2';
const COLOR_PRESET_KEY = 'examset_color_preset_v2';
const FONT_SCALE_KEY = 'examset_font_scale_v2';

const ThemeContext = createContext<ThemeContextValue | null>(null);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [themeMode, setThemeModeState] = useState<ThemeMode>(() => {
    try {
      const saved = localStorage.getItem(THEME_MODE_KEY) as ThemeMode | null;
      if (saved === 'LIGHT' || saved === 'DARK' || saved === 'SYSTEM') return saved;
    } catch {
      // ignore storage errors
    }
    return 'LIGHT';
  });

  const [preset, setPresetState] = useState<ColorSchemePreset>(() => {
    try {
      const saved = localStorage.getItem(COLOR_PRESET_KEY) as ColorSchemePreset | null;
      if (
        saved === 'NORDIC_SLATE' ||
        saved === 'WARM_PAPER' ||
        saved === 'FOREST_SAGE' ||
        saved === 'ROYAL_INDIGO' ||
        saved === 'OLED_OBSIDIAN'
      ) {
        return saved;
      }
    } catch {
      // ignore
    }
    return 'NORDIC_SLATE';
  });

  const [fontScaleMode, setFontScaleModeState] = useState<AppFontScaleMode>(() => {
    try {
      const saved = localStorage.getItem(FONT_SCALE_KEY) as AppFontScaleMode | null;
      if (FONT_SCALE_OPTIONS.some((o) => o.mode === saved)) return saved!;
    } catch {
      // ignore
    }
    return 'STANDARD_FIXED';
  });

  const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return false;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = (e: MediaQueryListEvent) => setSystemPrefersDark(e.matches);
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  }, []);

  const setThemeMode = (mode: ThemeMode) => {
    setThemeModeState(mode);
    try {
      localStorage.setItem(THEME_MODE_KEY, mode);
    } catch {
      // ignore
    }
  };

  const setPreset = (p: ColorSchemePreset) => {
    setPresetState(p);
    try {
      localStorage.setItem(COLOR_PRESET_KEY, p);
    } catch {
      // ignore
    }
  };

  const setFontScaleMode = (mode: AppFontScaleMode) => {
    setFontScaleModeState(mode);
    try {
      localStorage.setItem(FONT_SCALE_KEY, mode);
    } catch {
      // ignore
    }
  };

  const isDark = themeMode === 'DARK' || (themeMode === 'SYSTEM' && systemPrefersDark);
  const colors = useMemo(() => resolvePalette(preset, isDark), [preset, isDark]);
  const fontScale = useMemo(() => {
    const opt = FONT_SCALE_OPTIONS.find((o) => o.mode === fontScaleMode);
    return opt?.multiplier ?? 1.0;
  }, [fontScaleMode]);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    root.style.setProperty('--es-bg', colors.bg);
    root.style.setProperty('--es-surface', colors.surface);
    root.style.setProperty('--es-surface-variant', colors.surfaceVariant);
    root.style.setProperty('--es-border', colors.border);
    root.style.setProperty('--es-ink', colors.ink);
    root.style.setProperty('--es-ink-bold', colors.inkBold);
    root.style.setProperty('--es-muted', colors.muted);
    root.style.setProperty('--es-teal', colors.teal);
    root.style.setProperty('--es-banner', colors.navyBanner);
    root.style.colorScheme = isDark ? 'dark' : 'light';
  }, [colors, isDark]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      themeMode,
      setThemeMode,
      preset,
      setPreset,
      fontScaleMode,
      setFontScaleMode,
      fontScale,
      isDark,
      colors,
      scalePx: (basePx: number) => `${(basePx * fontScale).toFixed(2)}px`,
    }),
    [themeMode, preset, fontScaleMode, fontScale, isDark, colors]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    // Graceful fallback if ever rendered outside ThemeProvider
    const colors = resolvePalette('NORDIC_SLATE', false);
    return {
      themeMode: 'LIGHT',
      setThemeMode: () => {},
      preset: 'NORDIC_SLATE',
      setPreset: () => {},
      fontScaleMode: 'STANDARD_FIXED',
      setFontScaleMode: () => {},
      fontScale: 1.0,
      isDark: false,
      colors,
      scalePx: (px) => `${px}px`,
    };
  }
  return ctx;
}
