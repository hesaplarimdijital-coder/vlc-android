package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.alertBackground
import app.nagmedar.ui.components.successOnFill
import app.nagmedar.ui.components.tone
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-201 / S-1407: every text colour reaches 4.5:1 on every surface it is drawn on, in the light,
 * dark and OLED themes and with every accent (WCAG 2.2 AA for normal text).
 */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun palettes() = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled).flatMap { mode ->
        Accent.entries.map { accent -> "$mode/$accent" to NagPalette.colors(mode, systemDark = false, accent = accent) }
    }

    private fun check(failures: MutableList<String>, name: String, label: String, fg: Color, bg: Color, minimum: Double = 4.5) {
        val ratio = contrast(fg, bg)
        if (ratio < minimum) failures += "%s %s: %.2f:1".format(name, label, ratio)
    }

    @Test
    fun textOnSurfaces() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            val surfaces = mapOf("background" to c.background, "chrome" to c.chrome, "surface" to c.surface,
                "raised" to c.surfaceRaised, "hover" to c.hover)
            val texts = mapOf("text" to c.text, "secondary" to c.textSecondary, "tertiary" to c.textTertiary)
            for ((t, fg) in texts) for ((s, bg) in surfaces) check(failures, name, "$t on $s", fg, bg)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun accentAndSelection() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            check(failures, name, "accent text on background", c.accentText, c.background)
            check(failures, name, "accent text on hover", c.accentText, c.hover)
            check(failures, name, "on accent", c.onAccent, c.accent)
            check(failures, name, "on selection", c.onSelection, c.selection)
            check(failures, name, "tertiary on selection", c.textTertiary, c.selection)
            check(failures, name, "danger on raised", c.danger, c.surfaceRaised)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** S-308: badges, icon tiles, alerts, the success button and the undo bar. */
    @Test
    fun stateTones() {
        val failures = mutableListOf<String>()
        for ((name, c) in palettes()) {
            for (tone in StateTone.entries) {
                val t = c.tone(tone)
                check(failures, name, "$tone content on its container", t.content, t.container)
                check(failures, name, "$tone content on surface", t.content, c.surface)
                check(failures, name, "$tone content on background", t.content, c.background)
                check(failures, name, "$tone content on its alert", t.content, c.alertBackground(tone))
                check(failures, name, "secondary on $tone alert", c.textSecondary, c.alertBackground(tone))
                // icons are graphics: 3:1 (WCAG 1.4.11)
                check(failures, name, "$tone icon on its tile", t.icon, t.iconContainer, minimum = 3.0)
            }
            check(failures, name, "text on success fill", successOnFill(c), c.success)
            check(failures, name, "undo bar text", c.onInverseSurface, c.inverseSurface)
            check(failures, name, "undo bar action", c.inverseAccent, c.inverseSurface)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun systemModeFollowsTheSystem() {
        assertTrue(NagPalette.colors(ThemeMode.System, systemDark = true, accent = Accent.Blue).isDark)
        assertTrue(!NagPalette.colors(ThemeMode.System, systemDark = false, accent = Accent.Blue).isDark)
        assertTrue(NagPalette.colors(ThemeMode.Oled, systemDark = false, accent = Accent.Blue).background == Color.Black)
    }
}
