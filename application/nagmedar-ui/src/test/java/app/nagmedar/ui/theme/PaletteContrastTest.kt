package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-201 / S-308 / S-1407 / S-204: every text colour reaches 4.5:1 on every surface it is drawn on
 * (icons 3:1), in the light, dark and OLED themes, with every fixed accent and with the wallpaper
 * accent made from any wallpaper colour. The rules are [contrastIssues].
 */
class PaletteContrastTest {

    private val modes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled)

    @Test
    fun everyFixedAccentKeepsItsContrast() {
        val failures = modes.flatMap { mode ->
            Accent.entries.flatMap { accent ->
                NagPalette.colors(mode, systemDark = false, accent = accent).contrastIssues().map { "$mode/$accent $it" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theRulesCatchAPoorPalette() {
        val poor = NagPalette.dark(Accent.Blue).copy(textTertiary = Color(0xFF4A515A), accentText = Color(0xFF34466E))
        val issues = poor.contrastIssues()
        assertTrue(issues.joinToString(), issues.any { it.startsWith("tertiary on background") })
        assertTrue(issues.joinToString(), issues.any { it.startsWith("accent text on hover") })
    }

    /** Wallpaper colours: every 15° of hue at three saturations and four lightnesses, greys and extremes. */
    private val seeds: List<Color> = buildList {
        for (hue in 0 until 360 step 15) for (s in listOf(0.25f, 0.6f, 1f)) for (l in listOf(0.2f, 0.45f, 0.7f, 0.9f)) {
            add(Color.hsl(hue.toFloat(), s, l))
        }
        addAll(listOf(Color.White, Color.Black, Color.Gray, Color.Yellow, Color.Blue, Color.Cyan, Color.Magenta, Color(0xFF6750A4)))
    }

    @Test
    fun theWallpaperAccentKeepsContrastForAnyWallpaper() {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            val tones = AccentTones.fromSeed(seed)
            listOf("light" to NagPalette.light(tones), "dark" to NagPalette.dark(tones), "oled" to NagPalette.oled(tones)).forEach { (mode, colors) ->
                colors.contrastIssues().forEach { failures += "%08X %s: %s".format(seed.toArgb(), mode, it) }
            }
        }
        assertTrue(failures.take(30).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theWallpaperHueIsKept() {
        val lost = mutableListOf<String>()
        for (seed in seeds) {
            val source = OkLch.of(seed)
            if (source.c < 0.06f) continue // greys have no hue to keep
            val tones = AccentTones.fromSeed(seed)
            for ((name, tone) in listOf("base" to tones.base, "deep" to tones.deep)) {
                val derived = OkLch.of(tone)
                val drift = Math.abs(((derived.h - source.h + 540) % 360) - 180)
                if (derived.c < 0.03f || drift > 12f) lost += "%08X %s: hue %.0f → %.0f, chroma %.3f".format(seed.toArgb(), name, source.h, derived.h, derived.c)
            }
        }
        assertTrue(lost.take(30).joinToString("\n"), lost.isEmpty())
    }

    @Test
    fun systemModeFollowsTheSystem() {
        assertTrue(NagPalette.colors(ThemeMode.System, systemDark = true, accent = Accent.Blue).isDark)
        assertTrue(!NagPalette.colors(ThemeMode.System, systemDark = false, accent = Accent.Blue).isDark)
        assertTrue(NagPalette.colors(ThemeMode.Oled, systemDark = false, accent = Accent.Blue).background == Color.Black)
    }
}
