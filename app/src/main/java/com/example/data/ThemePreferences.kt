package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return when (value?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                else -> SYSTEM
            }
        }
    }
}

enum class ColorSchemePreset(

    val id: String,
    val title: String,
    val subtitle: String
) {
    NORDIC_SLATE("NORDIC_SLATE", "Nordic Slate", "Balanced cool slate & ocean cyan contrast"),
    WARM_PAPER("WARM_PAPER", "Warm Paper", "Soft amber & sepia parchment for eye comfort"),
    FOREST_SAGE("FOREST_SAGE", "Forest Sage", "Calm botanical emerald & eucalyptus"),
    ROYAL_INDIGO("ROYAL_INDIGO", "Royal Indigo", "Academic deep navy & rich violet accents"),
    OLED_OBSIDIAN("OLED_OBSIDIAN", "OLED Obsidian", "Pure pitch black background & high contrast"),
    MATERIAL_YOU("MATERIAL_YOU", "Dynamic System", "Follows device wallpaper dynamic palette (Android 12+)");

    companion object {
        fun fromString(value: String?): ColorSchemePreset {
            if (value.isNullOrBlank()) return NORDIC_SLATE
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.id.equals(value, ignoreCase = true)
            } ?: NORDIC_SLATE
        }
    }
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("examset_theme_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.fromString(saved)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getColorSchemePreset(): ColorSchemePreset {
        val saved = prefs.getString(KEY_COLOR_SCHEME_PRESET, ColorSchemePreset.NORDIC_SLATE.name)
        return ColorSchemePreset.fromString(saved)
    }

    fun setColorSchemePreset(preset: ColorSchemePreset) {
        prefs.edit().putString(KEY_COLOR_SCHEME_PRESET, preset.name).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_COLOR_SCHEME_PRESET = "key_color_scheme_preset"
    }
}

