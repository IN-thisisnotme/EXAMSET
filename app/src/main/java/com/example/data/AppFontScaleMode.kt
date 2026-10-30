package com.example.data

/**
 * Defines typography scale modes for ExamSet.
 *
 * STANDARD_FIXED locks font scale to exactly 1.0f (the app's intended standard size),
 * completely decoupling the typography from OS-level system font changes so question cards,
 * math formulas, tables, and OMR sheets are protected from aggressive wrapping or truncation.
 */
enum class AppFontScaleMode(
    val scaleFactor: Float,
    val title: String,
    val subtitle: String,
    val isSystemDependent: Boolean = false
) {
    STANDARD_FIXED(
        scaleFactor = 1.0f,
        title = "Standard (100% Fixed)",
        subtitle = "App baseline size. Immune to device system font changes.",
        isSystemDependent = false
    ),
    COMPACT(
        scaleFactor = 0.88f,
        title = "Compact (88% Fixed)",
        subtitle = "Fits more questions & options per screen cleanly.",
        isSystemDependent = false
    ),
    LARGE(
        scaleFactor = 1.15f,
        title = "Large (115% Fixed)",
        subtitle = "Comfortable reading size without table or equation distortion.",
        isSystemDependent = false
    ),
    EXTRA_LARGE(
        scaleFactor = 1.25f,
        title = "Extra Large (125% Fixed)",
        subtitle = "High visibility with balanced margins.",
        isSystemDependent = false
    ),
    FOLLOW_SYSTEM(
        scaleFactor = 1.0f,
        title = "Follow Device (System Dynamic)",
        subtitle = "Scales directly with Android OS display settings (safely clamped).",
        isSystemDependent = true
    );

    companion object {
        fun fromString(value: String?): AppFontScaleMode {
            if (value.isNullOrBlank()) return STANDARD_FIXED
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDARD_FIXED
        }
    }
}
