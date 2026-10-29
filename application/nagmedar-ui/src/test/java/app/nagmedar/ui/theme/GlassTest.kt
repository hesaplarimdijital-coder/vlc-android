package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Desktop Kit 1.0: glass keeps text readable over the worst content (white under the dark themes,
 * black under the light one), the device conditions pick the right mode, and an accent change
 * (the artwork's) cross-fades every colour, the glass tint with it.
 */
class GlassTest {

    private val seeds = listOf(Color(0xFFFFFF00), Color.White, Color.Black, Color(0xFF6750A4))

    private fun palettes(): List<Pair<String, NagColors>> = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled).flatMap { mode ->
        Accent.entries.map { accent -> "$mode/$accent" to NagPalette.colors(mode, systemDark = false, accent = accent) } +
            seeds.map { seed -> "$mode/seed" to NagPalette.colors(mode, systemDark = false, accent = Accent.System, systemSeed = seed) }
    }

    @Test
    fun textOnGlassOverTheWorstContent() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            val worst = if (c.isDark) Color.White else Color.Black
            for ((base, material) in listOf("chrome" to NagGlass::chrome, "raised" to NagGlass::raised)) {
                for (thickness in listOf(GlassThickness.Thin, GlassThickness.Regular)) {
                    val surface = NagGlass.composite(material(c, thickness), GlassMode.Blur, worst)
                    fun check(label: String, fg: Color, minimum: Double) {
                        val ratio = contrast(fg, surface)
                        if (ratio < minimum) failures += "%s %s %s %s: %.2f:1".format(name, base, thickness, label, ratio)
                    }
                    check("text", c.text, 4.5)
                    check("secondary", c.textSecondary, 3.0)
                }
                // without blur the tint is nearly opaque: every text colour stays at AA
                val translucent = NagGlass.composite(material(c, GlassThickness.Regular), GlassMode.Translucent, worst)
                for ((label, fg) in listOf("text" to c.text, "secondary" to c.textSecondary, "tertiary" to c.textTertiary)) {
                    val ratio = contrast(fg, translucent)
                    if (ratio < 4.5) failures += "%s %s translucent %s: %.2f:1".format(name, base, label, ratio)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun solidIsTheOpaqueSurface() {
        val c = NagPalette.dark(Accent.Blue)
        assertEquals(c.chrome, NagGlass.composite(NagGlass.chrome(c), GlassMode.Solid, Color.White))
        assertEquals(c.surfaceRaised, NagGlass.composite(NagGlass.raised(c), GlassMode.Solid, Color.White))
    }

    @Test
    fun thickerGlassIsMoreOpaque() {
        for ((_, c) in palettes()) {
            val alphas = GlassThickness.entries.map { NagGlass.chrome(c, it).tint.alpha }
            assertEquals(alphas.sorted(), alphas)
        }
    }

    @Test
    fun modeFollowsTheDevice() {
        assertEquals(GlassMode.Blur, NagGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Translucent, NagGlass.modeFor(sdk = 30, powerSave = false, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Translucent, NagGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = false, hardwareAccelerated = false))
        assertEquals(GlassMode.Solid, NagGlass.modeFor(sdk = 34, powerSave = true, reducedMotion = false, hardwareAccelerated = true))
        assertEquals(GlassMode.Solid, NagGlass.modeFor(sdk = 34, powerSave = false, reducedMotion = true, hardwareAccelerated = true))
        assertEquals(GlassMode.Solid, NagGlass.modeFor(sdk = 26, powerSave = true, reducedMotion = false, hardwareAccelerated = true))
    }

    @Test
    fun paletteCrossFadeEndsAtTheTarget() {
        val blue = NagPalette.dark(Accent.Blue)
        val coral = NagPalette.dark(Accent.Coral)
        assertEquals(coral, lerpColors(blue, coral, 1f))
        assertEquals(blue, lerpColors(blue, coral, 0f))
        val half = lerpColors(blue, coral, 0.5f)
        assertTrue(half.accent != blue.accent && half.accent != coral.accent)
        // glass made from the half-way palette sits between the two tints as well
        val tint = NagGlass.chrome(half).tint
        assertTrue(tint != NagGlass.chrome(blue).tint && tint != NagGlass.chrome(coral).tint)
        // a theme change too: the half-way frame takes the new theme's darkness
        assertEquals(false, lerpColors(blue, NagPalette.light(Accent.Blue), 0.5f).isDark)
    }

    @Test
    fun theArtworkAccentIsTheWallpaperAccentsTones() {
        // the player's accent from an artwork is made as the wallpaper's is, so it keeps every contrast (PaletteContrastTest)
        for (seed in seeds + Color(0xFF2E7D32)) {
            val tones = AccentTones.fromSeed(seed)
            assertEquals(NagPalette.light(tones), NagPalette.light(Accent.Blue).withAccent(tones))
            assertEquals(NagPalette.dark(tones), NagPalette.dark(Accent.Coral).withAccent(tones))
            assertEquals(NagPalette.oled(tones), NagPalette.oled(Accent.Green).withAccent(tones))
        }
    }
}
