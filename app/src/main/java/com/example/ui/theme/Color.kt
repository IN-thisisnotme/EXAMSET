package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Light Palette (Directly Aligned with Python MCQ Generator)
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

// ---------------------------------------------------------------------------
// Dark Palette (Optimized for Eye Safety & Low-Light Reading)
// ---------------------------------------------------------------------------
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
}

val LightExamColorPalette = ExamColorPalette(
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

val DarkExamColorPalette = ExamColorPalette(
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

val LocalExamColors = staticCompositionLocalOf { LightExamColorPalette }
