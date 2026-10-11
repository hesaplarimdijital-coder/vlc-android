package app.nagmedar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.alertBackground
import app.nagmedar.ui.components.successOnFill
import app.nagmedar.ui.components.tone

/** WCAG contrast ratio of two opaque colours, 1 to 21. */
fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * The contrast rules every palette keeps (S-201, S-308, S-204; WCAG 2.2 AA): text reaches 4.5:1
 * on every surface it is drawn on, accent text and fills, selections, the state tones and the undo
 * bar too; icons reach 3:1 (1.4.11). Returns what fails, empty when the palette passes.
 * PaletteContrastTest runs it on every palette, and the system accent is tuned until it passes.
 */
fun NagColors.contrastIssues(): List<String> {
    val issues = mutableListOf<String>()
    fun check(label: String, fg: Color, bg: Color, minimum: Double = 4.5) {
        val ratio = contrast(fg, bg)
        if (ratio < minimum) issues += "%s: %.2f:1".format(label, ratio)
    }
    val surfaces = listOf("background" to background, "chrome" to chrome, "surface" to surface, "raised" to surfaceRaised, "hover" to hover)
    val texts = listOf("text" to text, "secondary" to textSecondary, "tertiary" to textTertiary)
    for ((t, fg) in texts) for ((s, bg) in surfaces) check("$t on $s", fg, bg)

    check("accent text on background", accentText, background)
    check("accent text on hover", accentText, hover)
    check("on accent", onAccent, accent)
    check("on selection", onSelection, selection)
    check("tertiary on selection", textTertiary, selection)
    check("danger on raised", danger, surfaceRaised)

    for (state in StateTone.entries) {
        val t = tone(state)
        check("$state content on its container", t.content, t.container)
        check("$state content on surface", t.content, surface)
        check("$state content on background", t.content, background)
        check("$state content on its alert", t.content, alertBackground(state))
        check("secondary on $state alert", textSecondary, alertBackground(state))
        check("$state icon on its tile", t.icon, t.iconContainer, minimum = 3.0)
    }
    check("text on success fill", successOnFill(this), success)
    check("undo bar text", onInverseSurface, inverseSurface)
    check("undo bar action", inverseAccent, inverseSurface)
    return issues
}
