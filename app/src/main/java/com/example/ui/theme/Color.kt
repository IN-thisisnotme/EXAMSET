package com.example.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.ColorSchemePreset

// ---------------------------------------------------------------------------
// Nordic Slate Palette (Base Classic)
// ---------------------------------------------------------------------------
val ExamNavy = Color(0xFF17324D)
val ExamNavyDark = Color(0xFF0F2133)
val ExamTeal = Color(0xFF177E89)
val ExamTealDark = Color(0xFF12656D)
val ExamTealLight = Color(0xFFEAF6F6)
val ExamBlueLight = Color(0xFFF3F7FA)

val ExamInk = Color(0xFF0F172A)
val ExamPureBlack = Color(0xFF000000)
val ExamMuted = Color(0xFF64748B)
val ExamBorder = Color(0xFFD5E1E7)
val ExamBorderLight = Color(0xFFE2E8F0)
val ExamWhite = Color(0xFFFFFFFF)
val ExamBackground = Color(0xFFF8FAFC)
val ExamSurfaceVariant = Color(0xFFF1F5F9)

val ExamGreen = Color(0xFF247A55)
val ExamGreenLight = Color(0xFFEEF8F2)
val ExamAmber = Color(0xFFD97706)
val ExamAmberLight = Color(0xFFFFF8E8)
val ExamRed = Color(0xFFDC2626)
val ExamRedLight = Color(0xFFFEE2E2)

val DarkExamBackground = Color(0xFF0F172A)
val DarkExamSurface = Color(0xFF1E293B)
val DarkExamSurfaceElevated = Color(0xFF273549)
val DarkExamSurfaceVariant = Color(0xFF28364A)
val DarkExamBorder = Color(0xFF334155)
val DarkExamBorderLight = Color(0xFF2E3E52)

val DarkExamInk = Color(0xFFCBD5E1)
val DarkExamInkBold = Color(0xFFF8FAFC)
val DarkExamMuted = Color(0xFF94A3B8)

val DarkExamNavyBanner = Color(0xFF1E3A5F)
val DarkExamNavyBannerText = Color(0xFFF8FAFC)
val DarkExamTeal = Color(0xFF38BDF8)
val DarkExamTealDark = Color(0xFF0284C7)
val DarkExamTealLight = Color(0xFF162B3F)
val DarkExamBlueLight = Color(0xFF132235)

val DarkExamGreen = Color(0xFF34D399)
val DarkExamGreenLight = Color(0xFF132F23)
val DarkExamAmber = Color(0xFFFBBF24)
val DarkExamAmberLight = Color(0xFF38230D)
val DarkExamRed = Color(0xFFF87171)
val DarkExamRedLight = Color(0xFF381414)

@Immutable
data class ExamColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderLight: Color,
    val ink: Color,
    val inkBold: Color,
    val muted: Color,
    val navyBanner: Color,
    val navyBannerText: Color,
    val teal: Color,
    val tealDark: Color,
    val tealLight: Color,
    val blueLight: Color,
    val green: Color,
    val greenLight: Color,
    val amber: Color,
    val amberLight: Color,
    val red: Color,
    val redLight: Color,
    val printPaperBg: Color,
    val printPaperBorder: Color,
    val printPaperText: Color,
    val primaryButtonBg: Color,
    val primaryButtonText: Color,
    val secondaryButtonBg: Color,
    val secondaryButtonText: Color,
    val badgeSuccessBg: Color,
    val badgeSuccessText: Color,
    val badgeWarningBg: Color,
    val badgeWarningText: Color,
    val badgeDangerBg: Color,
    val badgeDangerText: Color
) {
    val inkMuted: Color get() = muted
    val yellow: Color get() = amber
    val yellowLight: Color get() = amberLight
    val accentPrimary: Color get() = teal
    val accentMutedBg: Color get() = tealLight
    val infoBadgeBg: Color get() = blueLight
    val infoBadgeText: Color get() = tealDark
}

// ---------------------------------------------------------------------------
// 1. Nordic Slate Palette (Default Balanced)
// ---------------------------------------------------------------------------
val NordicSlateLightPalette = ExamColorPalette(
    isDark = false,
    background = ExamBackground,
    surface = ExamWhite,
    surfaceElevated = ExamWhite,
    surfaceVariant = ExamSurfaceVariant,
    border = ExamBorder,
    borderLight = ExamBorderLight,
    ink = ExamInk,
    inkBold = ExamPureBlack,
    muted = ExamMuted,
    navyBanner = ExamNavy,
    navyBannerText = ExamWhite,
    teal = ExamTeal,
    tealDark = ExamTealDark,
    tealLight = ExamTealLight,
    blueLight = ExamBlueLight,
    green = ExamGreen,
    greenLight = ExamGreenLight,
    amber = ExamAmber,
    amberLight = ExamAmberLight,
    red = ExamRed,
    redLight = ExamRedLight,
    printPaperBg = ExamWhite,
    printPaperBorder = Color(0xFF334155),
    printPaperText = ExamPureBlack,
    primaryButtonBg = ExamNavy,
    primaryButtonText = ExamWhite,
    secondaryButtonBg = ExamSurfaceVariant,
    secondaryButtonText = ExamInk,
    badgeSuccessBg = ExamGreenLight,
    badgeSuccessText = ExamGreen,
    badgeWarningBg = ExamAmberLight,
    badgeWarningText = Color(0xFFB45309),
    badgeDangerBg = ExamRedLight,
    badgeDangerText = ExamRed
)

val NordicSlateDarkPalette = ExamColorPalette(
    isDark = true,
    background = DarkExamBackground,
    surface = DarkExamSurface,
    surfaceElevated = DarkExamSurfaceElevated,
    surfaceVariant = DarkExamSurfaceVariant,
    border = DarkExamBorder,
    borderLight = DarkExamBorderLight,
    ink = DarkExamInk,
    inkBold = DarkExamInkBold,
    muted = DarkExamMuted,
    navyBanner = DarkExamNavyBanner,
    navyBannerText = DarkExamNavyBannerText,
    teal = DarkExamTeal,
    tealDark = DarkExamTealDark,
    tealLight = DarkExamTealLight,
    blueLight = DarkExamBlueLight,
    green = DarkExamGreen,
    greenLight = DarkExamGreenLight,
    amber = DarkExamAmber,
    amberLight = DarkExamAmberLight,
    red = DarkExamRed,
    redLight = DarkExamRedLight,
    printPaperBg = Color(0xFF1A2330),
    printPaperBorder = Color(0xFF475569),
    printPaperText = Color(0xFFF1F5F9),
    primaryButtonBg = DarkExamTeal,
    primaryButtonText = Color(0xFF0F172A),
    secondaryButtonBg = DarkExamSurfaceElevated,
    secondaryButtonText = DarkExamInkBold,
    badgeSuccessBg = Color(0xFF064E3B),
    badgeSuccessText = Color(0xFF6EE7B7),
    badgeWarningBg = Color(0xFF451A03),
    badgeWarningText = Color(0xFFFCD34D),
    badgeDangerBg = Color(0xFF450A0A),
    badgeDangerText = Color(0xFFFCA5A5)
)

// Legacy aliases for full backward compatibility
val LightExamColorPalette = NordicSlateLightPalette
val DarkExamColorPalette = NordicSlateDarkPalette

// ---------------------------------------------------------------------------
// 2. Warm Paper Palette (Gentle Parchment / Eye Comfort)
// ---------------------------------------------------------------------------
val WarmPaperLightPalette = ExamColorPalette(
    isDark = false,
    background = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFDF9),
    surfaceVariant = Color(0xFFF3EDE2),
    border = Color(0xFFE4D8C5),
    borderLight = Color(0xFFECE3D4),
    ink = Color(0xFF2C241B),
    inkBold = Color(0xFF1B150E),
    muted = Color(0xFF7C6F5E),
    navyBanner = Color(0xFF382A1C),
    navyBannerText = Color(0xFFFAF7F2),
    teal = Color(0xFFB45309), // Terracotta/Amber accent
    tealDark = Color(0xFF92400E),
    tealLight = Color(0xFFFEF3C7),
    blueLight = Color(0xFFFFFBEB),
    green = Color(0xFF15803D),
    greenLight = Color(0xFFDCFCE7),
    amber = Color(0xFFD97706),
    amberLight = Color(0xFFFEF3C7),
    red = Color(0xFFB91C1C),
    redLight = Color(0xFFFEE2E2),
    printPaperBg = Color(0xFFFFFDF9),
    printPaperBorder = Color(0xFF44392E),
    printPaperText = Color(0xFF1B150E),
    primaryButtonBg = Color(0xFFB45309),
    primaryButtonText = Color(0xFFFFFFFF),
    secondaryButtonBg = Color(0xFFF3EDE2),
    secondaryButtonText = Color(0xFF2C241B),
    badgeSuccessBg = Color(0xFFDCFCE7),
    badgeSuccessText = Color(0xFF15803D),
    badgeWarningBg = Color(0xFFFEF3C7),
    badgeWarningText = Color(0xFF92400E),
    badgeDangerBg = Color(0xFFFEE2E2),
    badgeDangerText = Color(0xFFB91C1C)
)

val WarmPaperDarkPalette = ExamColorPalette(
    isDark = true,
    background = Color(0xFF1A1612), // Warm dark espresso
    surface = Color(0xFF241F1A),
    surfaceElevated = Color(0xFF2E2721),
    surfaceVariant = Color(0xFF2A231C),
    border = Color(0xFF42372C),
    borderLight = Color(0xFF382E24),
    ink = Color(0xFFDED3C4),
    inkBold = Color(0xFFFDF8F0),
    muted = Color(0xFFA09280),
    navyBanner = Color(0xFF332617),
    navyBannerText = Color(0xFFFDF8F0),
    teal = Color(0xFFF59E0B), // Golden amber glow
    tealDark = Color(0xFFD97706),
    tealLight = Color(0xFF382711),
    blueLight = Color(0xFF291E10),
    green = Color(0xFF4ADE80),
    greenLight = Color(0xFF15331C),
    amber = Color(0xFFFBBF24),
    amberLight = Color(0xFF3B250B),
    red = Color(0xFFF87171),
    redLight = Color(0xFF3D1212),
    printPaperBg = Color(0xFF231E18),
    printPaperBorder = Color(0xFF554637),
    printPaperText = Color(0xFFFDF8F0),
    primaryButtonBg = Color(0xFFF59E0B),
    primaryButtonText = Color(0xFF1A1612),
    secondaryButtonBg = Color(0xFF2E2721),
    secondaryButtonText = Color(0xFFFDF8F0),
    badgeSuccessBg = Color(0xFF0F3B1E),
    badgeSuccessText = Color(0xFF86EFAC),
    badgeWarningBg = Color(0xFF3D2307),
    badgeWarningText = Color(0xFFFDE68A),
    badgeDangerBg = Color(0xFF3D1111),
    badgeDangerText = Color(0xFFFCA5A5)
)

// ---------------------------------------------------------------------------
// 3. Forest Sage Palette (Calm Botanical Emerald & Eucalyptus)
// ---------------------------------------------------------------------------
val ForestSageLightPalette = ExamColorPalette(
    isDark = false,
    background = Color(0xFFF4F7F5),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EFEA),
    border = Color(0xFFC8D7CE),
    borderLight = Color(0xFFD8E4DC),
    ink = Color(0xFF15261C),
    inkBold = Color(0xFF0A170E),
    muted = Color(0xFF576E60),
    navyBanner = Color(0xFF173824),
    navyBannerText = Color(0xFFFFFFFF),
    teal = Color(0xFF15803D), // Sage green
    tealDark = Color(0xFF166534),
    tealLight = Color(0xFFE2F4E9),
    blueLight = Color(0xFFEDF7F1),
    green = Color(0xFF16A34A),
    greenLight = Color(0xFFDCFCE7),
    amber = Color(0xFFD97706),
    amberLight = Color(0xFFFFFBEB),
    red = Color(0xFFDC2626),
    redLight = Color(0xFFFEE2E2),
    printPaperBg = Color(0xFFFFFFFF),
    printPaperBorder = Color(0xFF334A3C),
    printPaperText = Color(0xFF0A170E),
    primaryButtonBg = Color(0xFF15803D),
    primaryButtonText = Color(0xFFFFFFFF),
    secondaryButtonBg = Color(0xFFE8EFEA),
    secondaryButtonText = Color(0xFF15261C),
    badgeSuccessBg = Color(0xFFDCFCE7),
    badgeSuccessText = Color(0xFF16A34A),
    badgeWarningBg = Color(0xFFFEF3C7),
    badgeWarningText = Color(0xFFB45309),
    badgeDangerBg = Color(0xFFFEE2E2),
    badgeDangerText = Color(0xFFDC2626)
)

val ForestSageDarkPalette = ExamColorPalette(
    isDark = true,
    background = Color(0xFF0C1610), // Deep evergreen forest night
    surface = Color(0xFF14241B),
    surfaceElevated = Color(0xFF1B3125),
    surfaceVariant = Color(0xFF172C20),
    border = Color(0xFF264433),
    borderLight = Color(0xFF203B2C),
    ink = Color(0xFFC8DBD0),
    inkBold = Color(0xFFF0FDF4),
    muted = Color(0xFF81A08E),
    navyBanner = Color(0xFF123420),
    navyBannerText = Color(0xFFF0FDF4),
    teal = Color(0xFF4ADE80), // Mint leaf
    tealDark = Color(0xFF22C55E),
    tealLight = Color(0xFF133621),
    blueLight = Color(0xFF0F2B1A),
    green = Color(0xFF34D399),
    greenLight = Color(0xFF0E301F),
    amber = Color(0xFFFBBF24),
    amberLight = Color(0xFF382309),
    red = Color(0xFFF87171),
    redLight = Color(0xFF381212),
    printPaperBg = Color(0xFF16261D),
    printPaperBorder = Color(0xFF3B5B47),
    printPaperText = Color(0xFFF0FDF4),
    primaryButtonBg = Color(0xFF4ADE80),
    primaryButtonText = Color(0xFF0C1610),
    secondaryButtonBg = Color(0xFF1B3125),
    secondaryButtonText = Color(0xFFF0FDF4),
    badgeSuccessBg = Color(0xFF0E3A20),
    badgeSuccessText = Color(0xFF86EFAC),
    badgeWarningBg = Color(0xFF3D2307),
    badgeWarningText = Color(0xFFFDE68A),
    badgeDangerBg = Color(0xFF3D1111),
    badgeDangerText = Color(0xFFFCA5A5)
)

// ---------------------------------------------------------------------------
// 4. Royal Indigo Palette (Academic Deep Navy & Violet)
// ---------------------------------------------------------------------------
val RoyalIndigoLightPalette = ExamColorPalette(
    isDark = false,
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEFF9),
    border = Color(0xFFCED5EC),
    borderLight = Color(0xFFDDE3F4),
    ink = Color(0xFF141930),
    inkBold = Color(0xFF080D21),
    muted = Color(0xFF5A6484),
    navyBanner = Color(0xFF1E1B4B), // Oxford navy
    navyBannerText = Color(0xFFFFFFFF),
    teal = Color(0xFF4F46E5), // Rich indigo
    tealDark = Color(0xFF4338CA),
    tealLight = Color(0xFFEEF2FF),
    blueLight = Color(0xFFF5F7FF),
    green = Color(0xFF16A34A),
    greenLight = Color(0xFFDCFCE7),
    amber = Color(0xFFD97706),
    amberLight = Color(0xFFFEF3C7),
    red = Color(0xFFDC2626),
    redLight = Color(0xFFFEE2E2),
    printPaperBg = Color(0xFFFFFFFF),
    printPaperBorder = Color(0xFF313959),
    printPaperText = Color(0xFF080D21),
    primaryButtonBg = Color(0xFF4F46E5),
    primaryButtonText = Color(0xFFFFFFFF),
    secondaryButtonBg = Color(0xFFECEFF9),
    secondaryButtonText = Color(0xFF141930),
    badgeSuccessBg = Color(0xFFDCFCE7),
    badgeSuccessText = Color(0xFF16A34A),
    badgeWarningBg = Color(0xFFFEF3C7),
    badgeWarningText = Color(0xFFB45309),
    badgeDangerBg = Color(0xFFFEE2E2),
    badgeDangerText = Color(0xFFDC2626)
)

val RoyalIndigoDarkPalette = ExamColorPalette(
    isDark = true,
    background = Color(0xFF0A0C1A), // Midnight indigo
    surface = Color(0xFF13172E),
    surfaceElevated = Color(0xFF1B2142),
    surfaceVariant = Color(0xFF171C38),
    border = Color(0xFF28325B),
    borderLight = Color(0xFF222B4D),
    ink = Color(0xFFC7CFEE),
    inkBold = Color(0xFFF5F7FF),
    muted = Color(0xFF838EB8),
    navyBanner = Color(0xFF191F44),
    navyBannerText = Color(0xFFF5F7FF),
    teal = Color(0xFF818CF8), // Periwinkle
    tealDark = Color(0xFF6366F1),
    tealLight = Color(0xFF1E234D),
    blueLight = Color(0xFF16193A),
    green = Color(0xFF34D399),
    greenLight = Color(0xFF0F2F1F),
    amber = Color(0xFFFBBF24),
    amberLight = Color(0xFF372208),
    red = Color(0xFFF87171),
    redLight = Color(0xFF381212),
    printPaperBg = Color(0xFF151933),
    printPaperBorder = Color(0xFF384478),
    printPaperText = Color(0xFFF5F7FF),
    primaryButtonBg = Color(0xFF818CF8),
    primaryButtonText = Color(0xFF0A0C1A),
    secondaryButtonBg = Color(0xFF1B2142),
    secondaryButtonText = Color(0xFFF5F7FF),
    badgeSuccessBg = Color(0xFF0E3821),
    badgeSuccessText = Color(0xFF86EFAC),
    badgeWarningBg = Color(0xFF3B2308),
    badgeWarningText = Color(0xFFFDE68A),
    badgeDangerBg = Color(0xFF3B1111),
    badgeDangerText = Color(0xFFFCA5A5)
)

// ---------------------------------------------------------------------------
// 5. OLED Obsidian Palette (True Pitch Black & Titanium)
// ---------------------------------------------------------------------------
val OledObsidianLightPalette = ExamColorPalette(
    isDark = false,
    background = Color(0xFFF9FAFB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3F4F6),
    border = Color(0xFFD1D5DB),
    borderLight = Color(0xFFE5E7EB),
    ink = Color(0xFF111827),
    inkBold = Color(0xFF030712),
    muted = Color(0xFF6B7280),
    navyBanner = Color(0xFF111827),
    navyBannerText = Color(0xFFFFFFFF),
    teal = Color(0xFF2563EB), // Electric cobalt
    tealDark = Color(0xFF1D4ED8),
    tealLight = Color(0xFFEFF6FF),
    blueLight = Color(0xFFF8FAFC),
    green = Color(0xFF16A34A),
    greenLight = Color(0xFFDCFCE7),
    amber = Color(0xFFD97706),
    amberLight = Color(0xFFFEF3C7),
    red = Color(0xFFDC2626),
    redLight = Color(0xFFFEE2E2),
    printPaperBg = Color(0xFFFFFFFF),
    printPaperBorder = Color(0xFF374151),
    printPaperText = Color(0xFF030712),
    primaryButtonBg = Color(0xFF111827),
    primaryButtonText = Color(0xFFFFFFFF),
    secondaryButtonBg = Color(0xFFF3F4F6),
    secondaryButtonText = Color(0xFF111827),
    badgeSuccessBg = Color(0xFFDCFCE7),
    badgeSuccessText = Color(0xFF16A34A),
    badgeWarningBg = Color(0xFFFEF3C7),
    badgeWarningText = Color(0xFFB45309),
    badgeDangerBg = Color(0xFFFEE2E2),
    badgeDangerText = Color(0xFFDC2626)
)

val OledObsidianDarkPalette = ExamColorPalette(
    isDark = true,
    background = Color(0xFF000000), // True AMOLED pitch black
    surface = Color(0xFF101010),
    surfaceElevated = Color(0xFF181818),
    surfaceVariant = Color(0xFF141414),
    border = Color(0xFF2C2C2C),
    borderLight = Color(0xFF222222),
    ink = Color(0xFFE4E4E4),
    inkBold = Color(0xFFFFFFFF),
    muted = Color(0xFFA1A1AA),
    navyBanner = Color(0xFF121212),
    navyBannerText = Color(0xFFFFFFFF),
    teal = Color(0xFF38BDF8),
    tealDark = Color(0xFF0284C7),
    tealLight = Color(0xFF142433),
    blueLight = Color(0xFF0F1822),
    green = Color(0xFF34D399),
    greenLight = Color(0xFF0D281C),
    amber = Color(0xFFFBBF24),
    amberLight = Color(0xFF2E1C06),
    red = Color(0xFFF87171),
    redLight = Color(0xFF2E0C0C),
    printPaperBg = Color(0xFF121212),
    printPaperBorder = Color(0xFF3F3F46),
    printPaperText = Color(0xFFFFFFFF),
    primaryButtonBg = Color(0xFF38BDF8),
    primaryButtonText = Color(0xFF000000),
    secondaryButtonBg = Color(0xFF181818),
    secondaryButtonText = Color(0xFFFFFFFF),
    badgeSuccessBg = Color(0xFF09311E),
    badgeSuccessText = Color(0xFF86EFAC),
    badgeWarningBg = Color(0xFF321A04),
    badgeWarningText = Color(0xFFFDE68A),
    badgeDangerBg = Color(0xFF320B0B),
    badgeDangerText = Color(0xFFFCA5A5)
)

// ---------------------------------------------------------------------------
// Dynamic Palette Resolvers
// ---------------------------------------------------------------------------
fun resolveExamColorPalette(
    preset: ColorSchemePreset,
    darkTheme: Boolean,
    context: Context
): ExamColorPalette {
    if (preset == ColorSchemePreset.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return ExamColorPalette(
            isDark = darkTheme,
            background = dynamicScheme.background,
            surface = dynamicScheme.surface,
            surfaceElevated = dynamicScheme.surfaceContainerHigh,
            surfaceVariant = dynamicScheme.surfaceVariant,
            border = dynamicScheme.outlineVariant,
            borderLight = dynamicScheme.outlineVariant.copy(alpha = 0.5f),
            ink = dynamicScheme.onSurface,
            inkBold = dynamicScheme.onSurface,
            muted = dynamicScheme.onSurfaceVariant,
            navyBanner = dynamicScheme.primaryContainer,
            navyBannerText = dynamicScheme.onPrimaryContainer,
            teal = dynamicScheme.primary,
            tealDark = dynamicScheme.secondary,
            tealLight = dynamicScheme.primaryContainer.copy(alpha = 0.35f),
            blueLight = dynamicScheme.secondaryContainer.copy(alpha = 0.25f),
            green = if (darkTheme) Color(0xFF34D399) else Color(0xFF16A34A),
            greenLight = if (darkTheme) Color(0xFF132F23) else Color(0xFFDCFCE7),
            amber = if (darkTheme) Color(0xFFFBBF24) else Color(0xFFD97706),
            amberLight = if (darkTheme) Color(0xFF38230D) else Color(0xFFFEF3C7),
            red = if (darkTheme) Color(0xFFF87171) else Color(0xFFDC2626),
            redLight = if (darkTheme) Color(0xFF381414) else Color(0xFFFEE2E2),
            printPaperBg = if (darkTheme) dynamicScheme.surfaceContainer else dynamicScheme.surface,
            printPaperBorder = dynamicScheme.outline,
            printPaperText = dynamicScheme.onSurface,
            primaryButtonBg = dynamicScheme.primary,
            primaryButtonText = dynamicScheme.onPrimary,
            secondaryButtonBg = dynamicScheme.surfaceVariant,
            secondaryButtonText = dynamicScheme.onSurfaceVariant,
            badgeSuccessBg = if (darkTheme) Color(0xFF064E3B) else Color(0xFFDCFCE7),
            badgeSuccessText = if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF16A34A),
            badgeWarningBg = if (darkTheme) Color(0xFF451A03) else Color(0xFFFEF3C7),
            badgeWarningText = if (darkTheme) Color(0xFFFCD34D) else Color(0xFFB45309),
            badgeDangerBg = if (darkTheme) Color(0xFF450A0A) else Color(0xFFFEE2E2),
            badgeDangerText = if (darkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626)
        )
    }

    return when (preset) {
        ColorSchemePreset.NORDIC_SLATE -> if (darkTheme) NordicSlateDarkPalette else NordicSlateLightPalette
        ColorSchemePreset.WARM_PAPER -> if (darkTheme) WarmPaperDarkPalette else WarmPaperLightPalette
        ColorSchemePreset.FOREST_SAGE -> if (darkTheme) ForestSageDarkPalette else ForestSageLightPalette
        ColorSchemePreset.ROYAL_INDIGO -> if (darkTheme) RoyalIndigoDarkPalette else RoyalIndigoLightPalette
        ColorSchemePreset.OLED_OBSIDIAN -> if (darkTheme) OledObsidianDarkPalette else OledObsidianLightPalette
        ColorSchemePreset.MATERIAL_YOU -> if (darkTheme) NordicSlateDarkPalette else NordicSlateLightPalette
    }
}

fun resolveMaterialColorScheme(
    preset: ColorSchemePreset,
    darkTheme: Boolean,
    context: Context
): ColorScheme {
    if (preset == ColorSchemePreset.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    val palette = resolveExamColorPalette(preset, darkTheme, context)
    return if (darkTheme) {
        darkColorScheme(
            primary = palette.teal,
            onPrimary = palette.background,
            primaryContainer = palette.tealLight,
            onPrimaryContainer = palette.teal,
            secondary = palette.tealDark,
            onSecondary = palette.navyBannerText,
            secondaryContainer = palette.blueLight,
            onSecondaryContainer = palette.teal,
            tertiary = palette.green,
            onTertiary = palette.background,
            tertiaryContainer = palette.greenLight,
            onTertiaryContainer = palette.green,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.inkBold,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.borderLight,
            inverseSurface = palette.surfaceElevated,
            inverseOnSurface = palette.inkBold,
            inversePrimary = palette.teal
        )
    } else {
        lightColorScheme(
            primary = palette.navyBanner,
            onPrimary = palette.navyBannerText,
            primaryContainer = palette.tealLight,
            onPrimaryContainer = palette.navyBanner,
            secondary = palette.teal,
            onSecondary = palette.navyBannerText,
            secondaryContainer = palette.tealLight,
            onSecondaryContainer = palette.tealDark,
            tertiary = palette.green,
            onTertiary = palette.navyBannerText,
            tertiaryContainer = palette.greenLight,
            onTertiaryContainer = palette.green,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.borderLight,
            inverseSurface = palette.navyBanner,
            inverseOnSurface = palette.navyBannerText,
            inversePrimary = palette.tealLight
        )
    }
}

val LocalExamColors = staticCompositionLocalOf { LightExamColorPalette }
