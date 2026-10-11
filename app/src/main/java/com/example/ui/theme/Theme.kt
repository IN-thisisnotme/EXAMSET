package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightColorScheme = lightColorScheme(
    primary = ExamNavy,
    onPrimary = ExamWhite,
    primaryContainer = ExamTealLight,
    onPrimaryContainer = ExamNavy,
    secondary = ExamTeal,
    onSecondary = ExamWhite,
    secondaryContainer = ExamTealLight,
    onSecondaryContainer = ExamTealDark,
    tertiary = ExamGreen,
    onTertiary = ExamWhite,
    tertiaryContainer = ExamGreenLight,
    onTertiaryContainer = ExamGreen,
    background = ExamBackground,
    onBackground = ExamInk,
    surface = ExamWhite,
    onSurface = ExamInk,
    surfaceVariant = ExamSurfaceVariant,
    onSurfaceVariant = ExamMuted,
    outline = ExamBorder,
    outlineVariant = ExamBorderLight,
    inverseSurface = ExamNavy,
    inverseOnSurface = ExamWhite,
    inversePrimary = ExamTealLight
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkExamTeal,
    onPrimary = DarkExamBackground,
    primaryContainer = DarkExamTealLight,
    onPrimaryContainer = DarkExamTeal,
    secondary = DarkExamTealDark,
    onSecondary = DarkExamNavyBannerText,
    secondaryContainer = DarkExamBlueLight,
    onSecondaryContainer = DarkExamTeal,
    tertiary = DarkExamGreen,
    onTertiary = DarkExamBackground,
    tertiaryContainer = DarkExamGreenLight,
    onTertiaryContainer = DarkExamGreen,
    background = DarkExamBackground,
    onBackground = DarkExamInk,
    surface = DarkExamSurface,
    onSurface = DarkExamInkBold,
    surfaceVariant = DarkExamSurfaceVariant,
    onSurfaceVariant = DarkExamMuted,
    outline = DarkExamBorder,
    outlineVariant = DarkExamBorderLight,
    inverseSurface = DarkExamSurfaceElevated,
    inverseOnSurface = DarkExamInkBold,
    inversePrimary = DarkExamTeal
)

object ExamTheme {
    val colors: ExamColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalExamColors.current
}

@Composable
fun ExamSetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val examPalette = if (darkTheme) DarkExamColorPalette else LightExamColorPalette

    CompositionLocalProvider(LocalExamColors provides examPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
