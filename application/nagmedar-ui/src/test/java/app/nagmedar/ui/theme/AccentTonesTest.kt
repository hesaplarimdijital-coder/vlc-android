package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** S-204: the fixed accents keep their tuned colours (1.1.0: the Ebru inks); the wallpaper's and the album's accents are derived from their seeds. */
class AccentTonesTest {

    @Test
    fun fixedAccentsKeepTheirColours() {
        val dark = NagPalette.dark(Accent.Blue)
        assertEquals(Color(0xFF84A9F8), dark.accent)
        assertEquals(Color(0xFF1E2D4C), dark.selection)
        assertEquals(Color(0xFFDDE8FF), dark.onSelection)
        assertEquals(Color(0xFF34549B), dark.inverseAccent)
        assertEquals(Color(0xFF3C1713), NagPalette.oled(Accent.Coral).selection)
        val light = NagPalette.light(Accent.Green)
        assertEquals(Color(0xFF007048), light.accent)
        assertEquals(Color(0xFFCFF6E0), light.selection)
        assertEquals(Accent.Green.base, light.inverseAccent)
    }

    @Test
    fun withoutAWallpaperColourTheSystemAccentIsBlue() {
        for (mode in ThemeMode.entries) {
            assertEquals(NagPalette.colors(mode, false, Accent.Blue), NagPalette.colors(mode, false, Accent.System, systemSeed = null))
            // 1.1.0: nothing with a coloured cover plays
            assertEquals(NagPalette.colors(mode, false, Accent.Blue), NagPalette.colors(mode, false, Accent.Album, systemSeed = null))
        }
    }

    @Test
    fun theSeedDecides() {
        val green = NagPalette.colors(ThemeMode.Dark, false, Accent.System, systemSeed = Color(0xFF2E7D32))
        val purple = NagPalette.colors(ThemeMode.Dark, false, Accent.System, systemSeed = Color(0xFF6750A4))
        assertTrue(green.accent != purple.accent)
        // green stays green: its hue is near 140° in OKLCH
        val hue = OkLch.of(green.accent).h
        assertTrue("hue $hue", hue in 120f..160f)
    }

    @Test
    fun okLchRoundTrips() {
        for (color in listOf(Color(0xFF8AB0FF), Color(0xFFF2B84B), Color(0xFF111418), Color(0xFFE6E9ED), Color.White, Color.Black)) {
            val back = OkLch.of(color).toColor()
            assertTrue("$color → $back", abs(back.red - color.red) < 0.004f && abs(back.green - color.green) < 0.004f && abs(back.blue - color.blue) < 0.004f)
        }
        // out of gamut: the chroma gives way, the lightness stays
        val vivid = OkLch(0.9f, 0.4f, 264f)
        val fitted = vivid.toColor()
        assertTrue(abs(OkLch.of(fitted).l - 0.9f) < 0.01f)
    }
}
