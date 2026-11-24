package com.example.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ColorSchemePreset
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePaletteTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("examset_theme_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun freshThemePreferences_defaultsToMinimalForLightAndDark() {
        val prefs = ThemePreferences(context)
        assertEquals(ThemeMode.SYSTEM, prefs.getThemeMode())
        assertEquals(ColorSchemePreset.MINIMAL, prefs.getColorSchemePreset())
        assertEquals(ColorSchemePreset.MINIMAL, ColorSchemePreset.fromString(null))
        assertEquals(ColorSchemePreset.MINIMAL, ColorSchemePreset.fromString("UNKNOWN_PRESET"))
        assertEquals(MinimalLightPalette, LightExamColorPalette)
        assertEquals(MinimalDarkPalette, DarkExamColorPalette)
    }

    @Test
    fun allCuratedThemePresets_resolveValidLightAndDarkPalettes() {
        assertTrue(ColorSchemePreset.entries.size >= 12)
        ColorSchemePreset.entries.forEach { preset ->
            val light = resolveExamColorPalette(preset, darkTheme = false, context = context)
            val dark = resolveExamColorPalette(preset, darkTheme = true, context = context)
            assertFalse("Light palette for $preset must report isDark=false", light.isDark)
            assertTrue("Dark palette for $preset must report isDark=true", dark.isDark)

            val m3Light = resolveMaterialColorScheme(preset, darkTheme = false, context = context)
            val m3Dark = resolveMaterialColorScheme(preset, darkTheme = true, context = context)
            assertEquals(light.background, m3Light.background)
            assertEquals(dark.background, m3Dark.background)
        }
    }
}
