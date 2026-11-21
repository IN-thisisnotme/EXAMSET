package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.ColorSchemePreset

object ExamTheme {
    val colors: ExamColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalExamColors.current
}

@Composable
fun ExamSetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePreset: ColorSchemePreset = ColorSchemePreset.NORDIC_SLATE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = resolveMaterialColorScheme(colorSchemePreset, darkTheme, context)
    val examPalette = resolveExamColorPalette(colorSchemePreset, darkTheme, context)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExamColors provides examPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
