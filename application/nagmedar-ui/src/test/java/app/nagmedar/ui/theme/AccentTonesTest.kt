package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** S-204: the fixed accents keep their hand-tuned colours; the wallpaper accent is derived from its seed. */
class AccentTonesTest {

    @Test
    fun fixedAccentsKeepTheirColours() {
        val dark = NagPalette.dark(Accent.Blue)
        assertEquals(Color(0xFF8AB0FF), dark.accent)
        assertEquals(Color(0xFF1C2A49), dark.selection)
        assertEquals(Color(0xFFDCE6FF), dark.onSelection)
        assertEquals(mixSrgb(Color(0xFF8AB0FF), Color.Black, 0.5f), dark.inverseAccent)
        assertEquals(Color(0xFF2E1816), NagPalette.oled(Accent.Coral).selection)
        val light = NagPalette.light(Accent.Green)
        assertEquals(mixSrgb(Accent.Green.base, Color.Black, 0.55f), light.accent)
        assertEquals(mixSrgb(Accent.Green.base, Color.White, 0.22f), light.selection)
        assertEquals(Accent.Green.base, light.inverseAccent)
    }

    @Test
    fun withoutAWallpaperColourTheSystemAccentIsBlue() {
        for (mode in ThemeMode.entries) {
            assertEquals(NagPalette.colors(mode, false, Accent.Blue), NagPalette.colors(mode, false, Accent.System, systemSeed = null))
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
