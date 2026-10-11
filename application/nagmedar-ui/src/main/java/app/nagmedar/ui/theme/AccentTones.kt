package app.nagmedar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What an accent gives the palettes (S-204): its light tone for the dark themes ([base]), its deep
 * tone for the light theme ([deep]), the tinted selection surfaces and the text on them, and the
 * accent of the undo bar on the dark themes ([darkInverse]). The four fixed accents are tuned by
 * hand; the system accent is derived from the wallpaper colour by [fromSeed].
 */
@Immutable
data class AccentTones(
    val base: Color,
    val deep: Color,
    val darkSelection: Color,
    val oledSelection: Color,
    val onDarkSelection: Color,
    val lightSelection: Color,
    val darkInverse: Color,
) {
    companion object {

        private fun fixed(base: Color, darkSelection: Color, oledSelection: Color, onDarkSelection: Color) = AccentTones(
            base = base,
            deep = mixSrgb(base, Color.Black, 0.55f),
            darkSelection = darkSelection,
            oledSelection = oledSelection,
            onDarkSelection = onDarkSelection,
            lightSelection = mixSrgb(base, Color.White, 0.22f),
            darkInverse = mixSrgb(base, Color.Black, 0.5f),
        )

        val Blue = fixed(Accent.Blue.base, Color(0xFF1C2A49), Color(0xFF142039), Color(0xFFDCE6FF))
        val Amber = fixed(Accent.Amber.base, Color(0xFF33290F), Color(0xFF2C230F), Color(0xFFF8E3BD))
        val Green = fixed(Accent.Green.base, Color(0xFF12301F), Color(0xFF102B1F), Color(0xFFCFF3E2))
        val Coral = fixed(Accent.Coral.base, Color(0xFF3A1E1B), Color(0xFF2E1816), Color(0xFFFFD9D4))

        /** [systemSeed] is the wallpaper colour; without one (Android 11 and older) the system accent is blue. */
        fun of(accent: Accent, systemSeed: Color? = null): AccentTones = when (accent) {
            Accent.Blue -> Blue
            Accent.Amber -> Amber
            Accent.Green -> Green
            Accent.Coral -> Coral
            Accent.System -> if (systemSeed == null) Blue else fromSeed(systemSeed)
        }

        /**
         * Tones in the seed's hue for any wallpaper: each tone starts where the fixed accents sit
         * and moves away from the surfaces it is drawn on, 0.02 of lightness at a time, until all
         * three palettes keep their contrast ([contrastIssues]). A tone that cannot get there keeps
         * the blue accent's, so the result always passes.
         */
        fun fromSeed(seed: Color): AccentTones {
            val lch = OkLch.of(seed)
            val chroma = lch.c.coerceAtMost(0.16f)
            fun tone(lightness: Float, maxChroma: Float) = OkLch(lightness, chroma.coerceAtMost(maxChroma), lch.h).toColor()
            fun steps(from: Float, to: Float): List<Float> {
                val count = (abs(to - from) / 0.02f).roundToInt()
                return (0..count).map { from + (to - from) * it / count.coerceAtLeast(1) }
            }

            var tones = Blue
            fun tune(lightness: List<Float>, candidate: AccentTones.(Float) -> AccentTones) {
                tones = lightness.asSequence().map { tones.candidate(it) }.firstOrNull { it.passes() } ?: tones
            }
            tune(steps(0.74f, 0.98f)) { copy(base = tone(it, 0.16f)) }
            tune(steps(0.30f, 0.12f)) { copy(darkSelection = tone(it, 0.06f), oledSelection = tone(it - 0.04f, 0.06f)) }
            tune(steps(0.93f, 0.99f)) { copy(onDarkSelection = tone(it, 0.04f)) }
            tune(steps(0.46f, 0.16f)) { copy(darkInverse = tone(it, 0.14f)) }
            tune(steps(0.50f, 0.20f)) { copy(deep = tone(it, 0.16f)) }
            tune(steps(0.94f, 0.99f)) { copy(lightSelection = tone(it, 0.05f)) }
            return tones
        }

        private fun AccentTones.passes(): Boolean =
            listOf(NagPalette.dark(this), NagPalette.oled(this), NagPalette.light(this)).all { it.contrastIssues().isEmpty() }
    }
}

/**
 * A colour in OKLCH: perceptual lightness [l] (0..1), chroma [c] and hue [h] in degrees. Moving
 * [l] keeps the hue, which mixing with black or white in sRGB does not.
 */
@Immutable
data class OkLch(val l: Float, val c: Float, val h: Float) {

    /** In sRGB; chroma is reduced until the colour fits the gamut, so the hue and lightness stay. */
    fun toColor(): Color {
        if (fits(c)) return srgb(c)!!
        var low = 0f
        var high = c
        repeat(20) {
            val mid = (low + high) / 2
            if (fits(mid)) low = mid else high = mid
        }
        return srgb(low) ?: Color(l, l, l)
    }

    private fun fits(chroma: Float) = srgb(chroma) != null

    /** The sRGB colour at [chroma], or null outside the gamut. */
    private fun srgb(chroma: Float): Color? {
        val rad = Math.toRadians(h.toDouble())
        val a = chroma * cos(rad)
        val b = chroma * sin(rad)
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val ll = l_ * l_ * l_
        val mm = m_ * m_ * m_
        val ss = s_ * s_ * s_
        val r = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
        val g = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
        val bl = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss
        val epsilon = 1e-4
        if (listOf(r, g, bl).any { it < -epsilon || it > 1 + epsilon }) return null
        return Color(encode(r), encode(g), encode(bl))
    }

    companion object {
        fun of(color: Color): OkLch {
            val r = decode(color.red)
            val g = decode(color.green)
            val b = decode(color.blue)
            val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val lightness = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
            val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
            val bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
            val hue = (Math.toDegrees(atan2(bb, a)) + 360) % 360
            return OkLch(lightness.toFloat(), hypot(a, bb).toFloat(), hue.toFloat())
        }

        private fun decode(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        private fun encode(linear: Double): Float {
            val c = linear.coerceIn(0.0, 1.0)
            val v = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
            return v.toFloat().coerceIn(0f, 1f)
        }
    }
}
