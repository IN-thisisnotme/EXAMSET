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

enum class ThemeCategory(val label: String) {
    ALL("All Themes"),
    MINIMAL("Minimal & Clean"),
    READING("Eye Comfort"),
    ACCENT("Focus & Contrast")
}

enum class ColorSchemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ThemeCategory = ThemeCategory.ACCENT,
    val styleTag: String = ""
) {
    MINIMAL(
        id = "MINIMAL",
        title = "Minimal",
        subtitle = "Crisp alabaster & matte graphite monochrome with zero visual clutter",
        category = ThemeCategory.MINIMAL,
        styleTag = "Default"
    ),
    STONE_LINEN(
        id = "STONE_LINEN",
        title = "Stone Linen",
        subtitle = "Soft architectural greige & warm espresso ink for quiet focus",
        category = ThemeCategory.MINIMAL,
        styleTag = "Editorial"
    ),
    NORDIC_SLATE(
        id = "NORDIC_SLATE",
        title = "Nordic Slate",
        subtitle = "Balanced cool slate & ocean cyan contrast",
        category = ThemeCategory.ACCENT,
        styleTag = "Classic"
    ),
    WARM_PAPER(
        id = "WARM_PAPER",
        title = "Warm Paper",
        subtitle = "Soft amber & sepia parchment for low-glare reading",
        category = ThemeCategory.READING,
        styleTag = "Parchment"
    ),
    SOLARIZED_INK(
        id = "SOLARIZED_INK",
        title = "Solarized Ink",
        subtitle = "Precision cream & midnight teal-slate engineered for long sessions",
        category = ThemeCategory.READING,
        styleTag = "Reading"
    ),
    FOREST_SAGE(
        id = "FOREST_SAGE",
        title = "Forest Sage",
        subtitle = "Calm botanical emerald & soft eucalyptus tones",
        category = ThemeCategory.READING,
        styleTag = "Botanical"
    ),
    OCEAN_DUSK(
        id = "OCEAN_DUSK",
        title = "Ocean Dusk",
        subtitle = "Sea-glass mist & deep marine slate serenity",
        category = ThemeCategory.ACCENT,
        styleTag = "Coastal"
    ),
    ROYAL_INDIGO(
        id = "ROYAL_INDIGO",
        title = "Royal Indigo",
        subtitle = "Academic deep navy & crisp indigo accents",
        category = ThemeCategory.ACCENT,
        styleTag = "Academic"
    ),
    TERRACOTTA_CLAY(
        id = "TERRACOTTA_CLAY",
        title = "Terracotta Clay",
        subtitle = "Warm limestone canvas with burnt sienna studio accents",
        category = ThemeCategory.READING,
        styleTag = "Warm Earth"
    ),
    ROSEWOOD_QUILL(
        id = "ROSEWOOD_QUILL",
        title = "Rosewood Quill",
        subtitle = "Editorial ivory paired with muted burgundy & plum ink",
        category = ThemeCategory.ACCENT,
        styleTag = "Editorial"
    ),
    MIDNIGHT_AMBER(
        id = "MIDNIGHT_AMBER",
        title = "Midnight Gold",
        subtitle = "Low-fatigue slate canvas with refined warm brass contrast",
        category = ThemeCategory.ACCENT,
        styleTag = "Studio"
    ),
    OLED_OBSIDIAN(
        id = "OLED_OBSIDIAN",
        title = "OLED Obsidian",
        subtitle = "Pure pitch black background & crisp high-contrast surfaces",
        category = ThemeCategory.MINIMAL,
        styleTag = "AMOLED"
    ),
    MATERIAL_YOU(
        id = "MATERIAL_YOU",
        title = "Dynamic System",
        subtitle = "Follows device wallpaper dynamic palette (Android 12+)",
        category = ThemeCategory.ACCENT,
        styleTag = "Adaptive"
    );

    companion object {
        fun fromString(value: String?): ColorSchemePreset {
            if (value.isNullOrBlank()) return MINIMAL
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.id.equals(value, ignoreCase = true)
            } ?: MINIMAL
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
        val saved = prefs.getString(KEY_COLOR_SCHEME_PRESET, ColorSchemePreset.MINIMAL.name)
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
