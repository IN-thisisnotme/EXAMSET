package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.data.AppFontScaleMode
import com.example.data.ThemeMode
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.ExamSetTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            // In-App Typography Scaling:
            // STANDARD_FIXED locks fontScale to 1.0f, immunizing the app against OS system font changes.
            // FOLLOW_SYSTEM safely clamps system font scale to prevent aggressive wrapping or truncation.
            val fontScaleMode by viewModel.fontScaleMode.collectAsState()
            val currentDensity = LocalDensity.current
            val effectiveFontScale = when (fontScaleMode) {
                AppFontScaleMode.FOLLOW_SYSTEM -> currentDensity.fontScale.coerceIn(0.85f, 1.35f)
                else -> fontScaleMode.scaleFactor
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = effectiveFontScale
                )
            ) {
                ExamSetTheme(darkTheme = isDark) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
