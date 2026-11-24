import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import {
  type AppFontScaleMode,
  type ColorSchemePreset,
  type ExamColorPalette,
  FONT_SCALE_OPTIONS,
  resolvePalette,
  type ThemeMode,
} from './colors';

interface ThemeContextValue {
  themeMode: ThemeMode;
  setThemeMode: (mode: ThemeMode) => void;
  preset: ColorSchemePreset;
  setPreset: (preset: ColorSchemePreset) => void;
  fontScaleMode: AppFontScaleMode;
  setFontScaleMode: (mode: AppFontScaleMode) => void;
  fontScale: number;
  browserBaselinePx: number;
  effectiveRootPx: number;
  isDark: boolean;
  colors: ExamColorPalette;
  /**
   * Scales consistently via root-relative rem units so Tailwind typography classes
   * and custom component font sizes stay in sync across browser, OS, and app scale modes
   * without double-multiplying.
   */
  scalePx: (basePx: number) => string;
  /** Numeric pixel equivalent under current active root font size. */
  rawScaledPx: (basePx: number) => number;
}

const THEME_MODE_KEY = 'examset_theme_mode_v2';
const COLOR_PRESET_KEY = 'examset_color_preset_v2';
const FONT_SCALE_KEY = 'examset_font_scale_v2';

const ThemeContext = createContext<ThemeContextValue | null>(null);

function measureBrowserDefaultFontPx(): number {
  if (typeof window === 'undefined' || typeof document === 'undefined') return 16;
  try {
    const probe = document.createElement('div');
    probe.style.cssText = 'font-size: medium; position: absolute; visibility: hidden; pointer-events: none;';
    document.body?.appendChild(probe);
    const computed = window.getComputedStyle(probe).fontSize;
    document.body?.removeChild(probe);
    const parsed = parseFloat(computed);
    if (Number.isFinite(parsed) && parsed >= 10 && parsed <= 36) {
      return Math.round(parsed * 100) / 100;
    }
  } catch {
    // ignore DOM probe error
  }
  return 16;
}

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

  const [browserBaselinePx, setBrowserBaselinePx] = useState<number>(() =>
    measureBrowserDefaultFontPx()
  );

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = (e: MediaQueryListEvent) => setSystemPrefersDark(e.matches);
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const updateBaseline = () => {
      setBrowserBaselinePx(measureBrowserDefaultFontPx());
    };
    updateBaseline();
    window.addEventListener('resize', updateBaseline);
    window.addEventListener('focus', updateBaseline);
    return () => {
      window.removeEventListener('resize', updateBaseline);
      window.removeEventListener('focus', updateBaseline);
    };
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
    if (fontScaleMode === 'FOLLOW_SYSTEM') {
      const clamped = Math.min(1.5, Math.max(0.8, browserBaselinePx / 16));
      return Math.round(clamped * 100) / 100;
    }
    const opt = FONT_SCALE_OPTIONS.find((o) => o.mode === fontScaleMode);
    return opt?.multiplier ?? 1.0;
  }, [fontScaleMode, browserBaselinePx]);

  const effectiveRootPx = useMemo(
    () => Math.round(16 * fontScale * 100) / 100,
    [fontScale]
  );

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
    root.style.setProperty('--es-font-scale', String(fontScale));
    root.style.colorScheme = isDark ? 'dark' : 'light';

    // Coordinate root rem font sizing cleanly across System, Browser, and Fixed App presets
    if (fontScaleMode === 'FOLLOW_SYSTEM') {
      // Keep user's browser/OS root preference intact, safely clamped via px if extreme
      const safeRatio = Math.min(1.5, Math.max(0.8, browserBaselinePx / 16));
      if (Math.abs(safeRatio - browserBaselinePx / 16) > 0.01) {
        root.style.fontSize = `${(16 * safeRatio).toFixed(2)}px`;
      } else {
        root.style.fontSize = '';
      }
    } else {
      root.style.fontSize = `${(16 * fontScale).toFixed(2)}px`;
    }
    root.setAttribute('data-font-scale', fontScaleMode);

    // Maintain a single scoped <style> block for cross-browser typography consistency,
    // max-2-line wrapping, smooth horizontal title scroll strips, and non-collapsing form fields.
    if (typeof document.head !== 'undefined') {
      let styleEl = document.getElementById('examset-global-theme-css') as HTMLStyleElement | null;
      if (!styleEl) {
        styleEl = document.createElement('style');
        styleEl.id = 'examset-global-theme-css';
        document.head.appendChild(styleEl);
      }
      styleEl.textContent = `
        html {
          -webkit-text-size-adjust: 100%;
          text-size-adjust: 100%;
          font-synthesis-weight: none;
        }
        body {
          background-color: var(--es-bg);
          color: var(--es-ink);
          -webkit-font-smoothing: antialiased;
          -moz-osx-font-smoothing: grayscale;
        }
        input, textarea, select, button {
          font-family: inherit;
        }
        textarea {
          line-height: 1.55;
        }
        input::placeholder, textarea::placeholder {
          color: var(--es-muted);
          opacity: 0.78;
        }
        .es-clamp-2 {
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
        .es-scroll-x {
          overflow-x: auto;
          white-space: nowrap;
          scrollbar-width: none;
          -ms-overflow-style: none;
          -webkit-overflow-scrolling: touch;
        }
        .es-scroll-x::-webkit-scrollbar {
          display: none;
        }
      `;
    }
  }, [colors, isDark, fontScale, fontScaleMode, browserBaselinePx]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      themeMode,
      setThemeMode,
      preset,
      setPreset,
      fontScaleMode,
      setFontScaleMode,
      fontScale,
      browserBaselinePx,
      effectiveRootPx,
      isDark,
      colors,
      scalePx: (basePx: number) => `${(basePx / 16).toFixed(4)}rem`,
      rawScaledPx: (basePx: number) => Math.round(basePx * fontScale * 100) / 100,
    }),
    [themeMode, preset, fontScaleMode, fontScale, browserBaselinePx, effectiveRootPx, isDark, colors]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    const colors = resolvePalette('NORDIC_SLATE', false);
    return {
      themeMode: 'LIGHT',
      setThemeMode: () => {},
      preset: 'NORDIC_SLATE',
      setPreset: () => {},
      fontScaleMode: 'STANDARD_FIXED',
      setFontScaleMode: () => {},
      fontScale: 1.0,
      browserBaselinePx: 16,
      effectiveRootPx: 16,
      isDark: false,
      colors,
      scalePx: (px) => `${(px / 16).toFixed(4)}rem`,
      rawScaledPx: (px) => px,
    };
  }
  return ctx;
}
