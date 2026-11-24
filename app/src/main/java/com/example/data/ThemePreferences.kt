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
    MINIMAL("Minimal"),
    READING("Eye Protection"),
    ACCENT("Calm Focus")
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
        subtitle = "Clean monochrome slate & matte graphite with calm steel accents",
        category = ThemeCategory.MINIMAL,
        styleTag = "Default"
    ),
    EYE_COMFORT(
        id = "EYE_COMFORT",
        title = "Eye Shield",
        subtitle = "Blue-light reduced warm cream & soft olive-sepia for long sessions",
        category = ThemeCategory.READING,
        styleTag = "Eye Care"
    ),
    WARM_PAPER(
        id = "WARM_PAPER",
        title = "Warm Paper",
        subtitle = "Gentle sepia parchment & muted ochre for low-glare reading",
        category = ThemeCategory.READING,
        styleTag = "Parchment"
    ),
    SOLARIZED_INK(
        id = "SOLARIZED_INK",
        title = "Solarized Ink",
        subtitle = "Precision cream & muted cyan-slate engineered for eye comfort",
        category = ThemeCategory.READING,
        styleTag = "Eye Care"
    ),
    STONE_LINEN(
        id = "STONE_LINEN",
        title = "Stone Linen",
        subtitle = "Soft architectural greige & muted espresso neutral tones",
        category = ThemeCategory.MINIMAL,
        styleTag = "Soft Matte"
    ),
    FOREST_SAGE(
        id = "FOREST_SAGE",
        title = "Forest Sage",
        subtitle = "Soothing botanical eucalyptus & soft moss green",
        category = ThemeCategory.READING,
        styleTag = "Botanical"
    ),
    OCEAN_DUSK(
        id = "OCEAN_DUSK",
        title = "Ocean Dusk",
        subtitle = "Calm coastal mist & muted deep marine slate",
        category = ThemeCategory.ACCENT,
        styleTag = "Coastal"
    ),
    NORDIC_SLATE(
        id = "NORDIC_SLATE",
        title = "Nordic Slate",
        subtitle = "Balanced cool slate & classic ocean cyan contrast",
        category = ThemeCategory.ACCENT,
        styleTag = "Classic"
    ),
    ROYAL_INDIGO(
        id = "ROYAL_INDIGO",
        title = "Royal Indigo",
        subtitle = "Academic deep navy & restrained slate-indigo accents",
        category = ThemeCategory.ACCENT,
        styleTag = "Academic"
    ),
    TERRACOTTA_CLAY(
        id = "TERRACOTTA_CLAY",
        title = "Terracotta Clay",
        subtitle = "Warm limestone canvas with muted burnt sienna tones",
        category = ThemeCategory.READING,
        styleTag = "Warm Earth"
    ),
    ROSEWOOD_QUILL(
        id = "ROSEWOOD_QUILL",
        title = "Rosewood Quill",
        subtitle = "Soft ivory paired with calm muted burgundy & plum ink",
        category = ThemeCategory.ACCENT,
        styleTag = "Editorial"
    ),
    MIDNIGHT_AMBER(
        id = "MIDNIGHT_AMBER",
        title = "Midnight Gold",
        subtitle = "Low-glare warm slate with restrained antique brass contrast",
        category = ThemeCategory.ACCENT,
        styleTag = "Studio"
    ),
    OLED_OBSIDIAN(
        id = "OLED_OBSIDIAN",
        title = "OLED Obsidian",
        subtitle = "Pure pitch-black background with softened titanium surfaces",
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
