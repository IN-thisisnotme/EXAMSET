export type ThemeMode = 'LIGHT' | 'DARK' | 'SYSTEM';

export type ColorSchemePreset =
  | 'NORDIC_SLATE'
  | 'WARM_PAPER'
  | 'FOREST_SAGE'
  | 'ROYAL_INDIGO'
  | 'OLED_OBSIDIAN';

export type AppFontScaleMode =
  | 'COMPACT'
  | 'STANDARD_FIXED'
  | 'LARGE'
  | 'EXTRA_LARGE'
  | 'FOLLOW_SYSTEM';

export interface FontScaleOption {
  mode: AppFontScaleMode;
  label: string;
  multiplier: number;
  description: string;
}

export const FONT_SCALE_OPTIONS: FontScaleOption[] = [
  {
    mode: 'COMPACT',
    label: 'Compact (88% Fixed)',
    multiplier: 0.88,
    description: 'High information density; fits more questions & options per viewport',
  },
  {
    mode: 'STANDARD_FIXED',
    label: 'Standard (100% Fixed)',
    multiplier: 1.0,
    description: '16px app baseline; shields exam tables & formulas from OS overrides',
  },
  {
    mode: 'LARGE',
    label: 'Large (115% Fixed)',
    multiplier: 1.15,
    description: 'Comfortable long-session reading with balanced card wrapping',
  },
  {
    mode: 'EXTRA_LARGE',
    label: 'Extra Large (125% Fixed)',
    multiplier: 1.25,
    description: 'High-visibility scaling across cards, options, and navigation',
  },
  {
    mode: 'FOLLOW_SYSTEM',
    label: 'Follow Device & Browser (Dynamic)',
    multiplier: 1.0,
    description: 'Adapts directly to OS display scaling and browser root font preferences',
  },
];

export interface ExamColorPalette {
  bg: string;
  surface: string;
  surfaceElevated: string;
  surfaceVariant: string;
  border: string;
  borderLight: string;
  ink: string;
  inkBold: string;
  inkMuted: string;
  muted: string;
  navyBanner: string;
  navyBannerText: string;
  teal: string;
  tealDark: string;
  tealLight: string;
  blueLight: string;
  green: string;
  greenLight: string;
  amber: string;
  amberLight: string;
  red: string;
  redLight: string;
  primaryButtonBg: string;
  primaryButtonText: string;
  badgeSuccessBg: string;
  badgeSuccessText: string;
  badgeWarningBg: string;
  badgeWarningText: string;
  badgeDangerBg: string;
  badgeDangerText: string;
  purple: string;
  purpleLight: string;
}

export const PRESET_METADATA: {
  id: ColorSchemePreset;
  name: string;
  subtitle: string;
  swatches: [string, string, string];
}[] = [
  {
    id: 'NORDIC_SLATE',
    name: 'Nordic Slate',
    subtitle: 'Crisp navy & medical teal',
    swatches: ['#17324D', '#177E89', '#F8FAFC'],
  },
  {
    id: 'WARM_PAPER',
    name: 'Warm Paper',
    subtitle: 'Eye-ease sepia & amber ink',
    swatches: ['#382A1C', '#B45309', '#FAF7F2'],
  },
  {
    id: 'FOREST_SAGE',
    name: 'Forest Sage',
    subtitle: 'Calm botanical emerald',
    swatches: ['#173824', '#15803D', '#F4F7F5'],
  },
  {
    id: 'ROYAL_INDIGO',
    name: 'Royal Indigo',
    subtitle: 'Scholarly violet & slate',
    swatches: ['#1E1B4B', '#4F46E5', '#F6F7FB'],
  },
  {
    id: 'OLED_OBSIDIAN',
    name: 'OLED Obsidian',
    subtitle: 'High-contrast crisp monochrome',
    swatches: ['#111827', '#2563EB', '#F9FAFB'],
  },
];

const LIGHT_PALETTES: Record<ColorSchemePreset, ExamColorPalette> = {
  NORDIC_SLATE: {
    bg: '#F8FAFC',
    surface: '#FFFFFF',
    surfaceElevated: '#FFFFFF',
    surfaceVariant: '#F1F5F9',
    border: '#D5E1E7',
    borderLight: '#E2E8F0',
    ink: '#0F172A',
    inkBold: '#000000',
    inkMuted: '#475569',
    muted: '#64748B',
    navyBanner: '#17324D',
    navyBannerText: '#FFFFFF',
    teal: '#177E89',
    tealDark: '#12656D',
    tealLight: '#EAF6F6',
    blueLight: '#F3F7FA',
    green: '#247A55',
    greenLight: '#EEF8F2',
    amber: '#D97706',
    amberLight: '#FFF8E8',
    red: '#DC2626',
    redLight: '#FEE2E2',
    primaryButtonBg: '#17324D',
    primaryButtonText: '#FFFFFF',
    badgeSuccessBg: '#EEF8F2',
    badgeSuccessText: '#247A55',
    badgeWarningBg: '#FFF8E8',
    badgeWarningText: '#B45309',
    badgeDangerBg: '#FEE2E2',
    badgeDangerText: '#DC2626',
    purple: '#6D28D9',
    purpleLight: '#F3E8FF',
  },
  WARM_PAPER: {
    bg: '#FAF7F2',
    surface: '#FFFDF9',
    surfaceElevated: '#FFFFFF',
    surfaceVariant: '#F2ECE1',
    border: '#E6DDD0',
    borderLight: '#EFE8DC',
    ink: '#29221A',
    inkBold: '#14100C',
    inkMuted: '#6E6254',
    muted: '#7A6E60',
    navyBanner: '#382A1C',
    navyBannerText: '#FFFFFF',
    teal: '#B45309',
    tealDark: '#92400E',
    tealLight: '#FEF3C7',
    blueLight: '#F6F1E9',
    green: '#15803D',
    greenLight: '#ECFDF5',
    amber: '#D97706',
    amberLight: '#FEF3C7',
    red: '#B91C1C',
    redLight: '#FEE2E2',
    primaryButtonBg: '#382A1C',
    primaryButtonText: '#FFFFFF',
    badgeSuccessBg: '#ECFDF5',
    badgeSuccessText: '#15803D',
    badgeWarningBg: '#FEF3C7',
    badgeWarningText: '#B45309',
    badgeDangerBg: '#FEE2E2',
    badgeDangerText: '#B91C1C',
    purple: '#7C3AED',
    purpleLight: '#F3E8FF',
  },
  FOREST_SAGE: {
    bg: '#F4F7F5',
    surface: '#FFFFFF',
    surfaceElevated: '#FFFFFF',
    surfaceVariant: '#E9F0EC',
    border: '#D0DFD5',
    borderLight: '#E1ECE5',
    ink: '#132A1C',
    inkBold: '#08170D',
    inkMuted: '#4B6354',
    muted: '#5D7767',
    navyBanner: '#173824',
    navyBannerText: '#FFFFFF',
    teal: '#15803D',
    tealDark: '#166534',
    tealLight: '#DCFCE7',
    blueLight: '#EDF4EF',
    green: '#15803D',
    greenLight: '#DCFCE7',
    amber: '#D97706',
    amberLight: '#FEF3C7',
    red: '#DC2626',
    redLight: '#FEE2E2',
    primaryButtonBg: '#173824',
    primaryButtonText: '#FFFFFF',
    badgeSuccessBg: '#DCFCE7',
    badgeSuccessText: '#166534',
    badgeWarningBg: '#FEF3C7',
    badgeWarningText: '#B45309',
    badgeDangerBg: '#FEE2E2',
    badgeDangerText: '#DC2626',
    purple: '#6D28D9',
    purpleLight: '#F3E8FF',
  },
  ROYAL_INDIGO: {
    bg: '#F6F7FB',
    surface: '#FFFFFF',
    surfaceElevated: '#FFFFFF',
    surfaceVariant: '#ECEEF6',
    border: '#D5DAF0',
    borderLight: '#E4E8F5',
    ink: '#1E1B4B',
    inkBold: '#0F0D2E',
    inkMuted: '#4C517B',
    muted: '#646994',
    navyBanner: '#1E1B4B',
    navyBannerText: '#FFFFFF',
    teal: '#4F46E5',
    tealDark: '#4338CA',
    tealLight: '#EEF2FF',
    blueLight: '#F0F2FB',
    green: '#15803D',
    greenLight: '#DCFCE7',
    amber: '#D97706',
    amberLight: '#FEF3C7',
    red: '#DC2626',
    redLight: '#FEE2E2',
    primaryButtonBg: '#1E1B4B',
    primaryButtonText: '#FFFFFF',
    badgeSuccessBg: '#DCFCE7',
    badgeSuccessText: '#15803D',
    badgeWarningBg: '#FEF3C7',
    badgeWarningText: '#B45309',
    badgeDangerBg: '#FEE2E2',
    badgeDangerText: '#DC2626',
    purple: '#7C3AED',
    purpleLight: '#F3E8FF',
  },
  OLED_OBSIDIAN: {
    bg: '#F9FAFB',
    surface: '#FFFFFF',
    surfaceElevated: '#FFFFFF',
    surfaceVariant: '#F3F4F6',
    border: '#E5E7EB',
    borderLight: '#F1F5F9',
    ink: '#111827',
    inkBold: '#000000',
    inkMuted: '#4B5563',
    muted: '#6B7280',
    navyBanner: '#111827',
    navyBannerText: '#FFFFFF',
    teal: '#2563EB',
    tealDark: '#1D4ED8',
    tealLight: '#EFF6FF',
    blueLight: '#F3F4F6',
    green: '#15803D',
    greenLight: '#DCFCE7',
    amber: '#D97706',
    amberLight: '#FEF3C7',
    red: '#DC2626',
    redLight: '#FEE2E2',
    primaryButtonBg: '#111827',
    primaryButtonText: '#FFFFFF',
    badgeSuccessBg: '#DCFCE7',
    badgeSuccessText: '#15803D',
    badgeWarningBg: '#FEF3C7',
    badgeWarningText: '#B45309',
    badgeDangerBg: '#FEE2E2',
    badgeDangerText: '#DC2626',
    purple: '#6D28D9',
    purpleLight: '#F3E8FF',
  },
};

const DARK_PALETTES: Record<ColorSchemePreset, ExamColorPalette> = {
  NORDIC_SLATE: {
    bg: '#0F172A',
    surface: '#1E293B',
    surfaceElevated: '#273549',
    surfaceVariant: '#28364A',
    border: '#334155',
    borderLight: '#2E3E52',
    ink: '#CBD5E1',
    inkBold: '#F8FAFC',
    inkMuted: '#94A3B8',
    muted: '#94A3B8',
    navyBanner: '#1E3A5F',
    navyBannerText: '#F8FAFC',
    teal: '#38BDF8',
    tealDark: '#0284C7',
    tealLight: '#162B3F',
    blueLight: '#132235',
    green: '#34D399',
    greenLight: '#132F23',
    amber: '#FBBF24',
    amberLight: '#38230D',
    red: '#F87171',
    redLight: '#381414',
    primaryButtonBg: '#38BDF8',
    primaryButtonText: '#0F172A',
    badgeSuccessBg: '#064E3B',
    badgeSuccessText: '#6EE7B7',
    badgeWarningBg: '#451A03',
    badgeWarningText: '#FCD34D',
    badgeDangerBg: '#450A0A',
    badgeDangerText: '#FCA5A5',
    purple: '#A78BFA',
    purpleLight: '#2E1065',
  },
  WARM_PAPER: {
    bg: '#1A1612',
    surface: '#26201B',
    surfaceElevated: '#322A23',
    surfaceVariant: '#332B24',
    border: '#443930',
    borderLight: '#3A3028',
    ink: '#EAE2D6',
    inkBold: '#FAF7F2',
    inkMuted: '#A89B8B',
    muted: '#A89B8B',
    navyBanner: '#362717',
    navyBannerText: '#FAF7F2',
    teal: '#F59E0B',
    tealDark: '#D97706',
    tealLight: '#332309',
    blueLight: '#2A221A',
    green: '#4ADE80',
    greenLight: '#122D1B',
    amber: '#FBBF24',
    amberLight: '#38230D',
    red: '#F87171',
    redLight: '#381414',
    primaryButtonBg: '#F59E0B',
    primaryButtonText: '#1A1612',
    badgeSuccessBg: '#064E3B',
    badgeSuccessText: '#6EE7B7',
    badgeWarningBg: '#451A03',
    badgeWarningText: '#FCD34D',
    badgeDangerBg: '#450A0A',
    badgeDangerText: '#FCA5A5',
    purple: '#C4B5FD',
    purpleLight: '#2E1065',
  },
  FOREST_SAGE: {
    bg: '#0C1610',
    surface: '#14241A',
    surfaceElevated: '#1C3024',
    surfaceVariant: '#1D3326',
    border: '#2B4736',
    borderLight: '#223A2C',
    ink: '#DAE8DF',
    inkBold: '#F0FDF4',
    inkMuted: '#88A793',
    muted: '#88A793',
    navyBanner: '#173824',
    navyBannerText: '#F0FDF4',
    teal: '#4ADE80',
    tealDark: '#22C55E',
    tealLight: '#10301E',
    blueLight: '#11251A',
    green: '#4ADE80',
    greenLight: '#122D1B',
    amber: '#FBBF24',
    amberLight: '#38230D',
    red: '#F87171',
    redLight: '#381414',
    primaryButtonBg: '#4ADE80',
    primaryButtonText: '#0C1610',
    badgeSuccessBg: '#064E3B',
    badgeSuccessText: '#6EE7B7',
    badgeWarningBg: '#451A03',
    badgeWarningText: '#FCD34D',
    badgeDangerBg: '#450A0A',
    badgeDangerText: '#FCA5A5',
    purple: '#C4B5FD',
    purpleLight: '#2E1065',
  },
  ROYAL_INDIGO: {
    bg: '#0A0C1A',
    surface: '#13172E',
    surfaceElevated: '#1B2040',
    surfaceVariant: '#1E2342',
    border: '#2D3461',
    borderLight: '#242A52',
    ink: '#DCE0F5',
    inkBold: '#F8FAFC',
    inkMuted: '#9299C2',
    muted: '#9299C2',
    navyBanner: '#1E1B4B',
    navyBannerText: '#F8FAFC',
    teal: '#818CF8',
    tealDark: '#6366F1',
    tealLight: '#1C1E42',
    blueLight: '#161936',
    green: '#34D399',
    greenLight: '#132F23',
    amber: '#FBBF24',
    amberLight: '#38230D',
    red: '#F87171',
    redLight: '#381414',
    primaryButtonBg: '#818CF8',
    primaryButtonText: '#0A0C1A',
    badgeSuccessBg: '#064E3B',
    badgeSuccessText: '#6EE7B7',
    badgeWarningBg: '#451A03',
    badgeWarningText: '#FCD34D',
    badgeDangerBg: '#450A0A',
    badgeDangerText: '#FCA5A5',
    purple: '#C4B5FD',
    purpleLight: '#2E1065',
  },
  OLED_OBSIDIAN: {
    bg: '#000000',
    surface: '#101010',
    surfaceElevated: '#1A1A1A',
    surfaceVariant: '#1C1C1C',
    border: '#2E2E2E',
    borderLight: '#242424',
    ink: '#E5E7EB',
    inkBold: '#FFFFFF',
    inkMuted: '#9CA3AF',
    muted: '#9CA3AF',
    navyBanner: '#1A1A1A',
    navyBannerText: '#FFFFFF',
    teal: '#38BDF8',
    tealDark: '#0284C7',
    tealLight: '#0F2536',
    blueLight: '#141414',
    green: '#34D399',
    greenLight: '#0E291F',
    amber: '#FBBF24',
    amberLight: '#331F0B',
    red: '#F87171',
    redLight: '#361111',
    primaryButtonBg: '#38BDF8',
    primaryButtonText: '#000000',
    badgeSuccessBg: '#064E3B',
    badgeSuccessText: '#6EE7B7',
    badgeWarningBg: '#451A03',
    badgeWarningText: '#FCD34D',
    badgeDangerBg: '#450A0A',
    badgeDangerText: '#FCA5A5',
    purple: '#C4B5FD',
    purpleLight: '#2E1065',
  },
};

export function resolvePalette(preset: ColorSchemePreset, isDark: boolean): ExamColorPalette {
  return isDark ? DARK_PALETTES[preset] : LIGHT_PALETTES[preset];
}
