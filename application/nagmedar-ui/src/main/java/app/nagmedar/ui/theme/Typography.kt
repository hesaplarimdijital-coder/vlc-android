package app.nagmedar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R

/** 650: the titles, the primary buttons and the overlines, between semibold and bold. */
val SemiBoldPlus = FontWeight(650)

/**
 * 1.1.0 (Ebru): Bricolage Grotesque for the interface, instances of its variable font at the 14 pt optical size and
 * full width, one file a weight (SIL OFL 1.1; the files were made with fontTools' instancer, so nothing depends on a
 * renderer's variation support).
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_400, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_500, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_600, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_650, SemiBoldPlus),
    Font(R.font.bricolage_grotesque_700, FontWeight.Bold),
)

/**
 * 1.1.0 (Ebru): Bodoni Moda's italic for the titles, each at the optical size it is read at (SIL OFL 1.1): the page's
 * masthead ("Müzikler") at 48 pt, the display lines (a letter of the alphabet, what plays) at 28 pt, the headlines (a
 * dialog's question, a pane's title) at 20 pt and a weight heavier.
 */
val BodoniMasthead = FontFamily(Font(R.font.bodoni_moda_italic_masthead, FontWeight.Medium, FontStyle.Italic))
val BodoniDisplay = FontFamily(Font(R.font.bodoni_moda_italic_display, FontWeight.Medium, FontStyle.Italic))
val BodoniHeadline = FontFamily(Font(R.font.bodoni_moda_italic_headline, FontWeight.SemiBold, FontStyle.Italic))

/** IBM Plex Mono for timecodes, formats and key caps (SIL OFL 1.1). */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/**
 * Text styles. Two sets: pointer (DeX, mouse) is denser, touch is larger. Nothing is below 11 sp.
 * On tablet and DeX windows the theme sets every style [step] sp larger ([grow], Desktop Kit 0.2.0);
 * the masthead grows [MASTHEAD_GROWTH] times as much (52 → 60 sp on a tablet).
 *
 * 1.1.0 (Ebru): [masthead], [display] and [headline] are Bodoni Moda's italic; the rest is Bricolage
 * Grotesque, [mono] IBM Plex Mono. [overline] is the small capitals of a section's caption ("KÜTÜPHANE"):
 * the text itself is uppercased where it is shown, with the reader's locale.
 */
@Immutable
data class NagType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle,
    /** The sp [grow] added to every style; [size] adds it to a size of the code's own. */
    val step: Float = 0f,
    /** 1.1.0: a page's title, large, over its ebru band. */
    val masthead: TextStyle = display,
    /** 1.1.0: a section's caption, in capitals with a little air between them. */
    val overline: TextStyle = caption,
) {
    /**
     * The same styles [by] sp larger (smaller when negative); line heights keep their ratio to the
     * size. The theme uses it to set text one sp larger on tablet and DeX windows.
     */
    fun grow(by: Float): NagType {
        if (by == 0f) return this
        fun TextStyle.grown(amount: Float = by): TextStyle {
            val size = fontSize.value
            val ratio = if (lineHeight.isSp && size > 0f) lineHeight.value / size else Float.NaN
            val next = size + amount
            return copy(fontSize = next.sp, lineHeight = if (ratio.isNaN()) lineHeight else (next * ratio).sp)
        }
        return NagType(
            display = display.grown(),
            headline = headline.grown(),
            title = title.grown(),
            body = body.grown(),
            bodyStrong = bodyStrong.grown(),
            label = label.grown(),
            labelSmall = labelSmall.grown(),
            caption = caption.grown(),
            mono = mono.grown(),
            step = step + by,
            masthead = masthead.grown(by * MASTHEAD_GROWTH),
            overline = overline.grown(),
        )
    }

    /** [base] sp, a size a screen sets itself (a chart's label, the jump dialog's time), with the window's [step]. */
    fun size(base: Float): TextUnit = (base + step).sp

    companion object {
        /** How many times the window's step the masthead grows. */
        const val MASTHEAD_GROWTH = 8f

        val Pointer = build(
            masthead = 44 to 48, display = 26 to 32, headline = 20 to 26, title = 16 to 22, body = 13 to 18,
            label = 12.5f to 16, labelSmall = 11.5f to 15, caption = 11 to 14, mono = 11 to 14,
        )
        val Touch = build(
            masthead = 52 to 56, display = 30 to 36, headline = 22 to 28, title = 17 to 23, body = 15 to 21,
            label = 14 to 18, labelSmall = 13 to 17, caption = 12 to 16, mono = 12 to 16,
        )

        fun forMode(mode: InputMode) = if (mode == InputMode.Pointer) Pointer else Touch

        private fun build(
            masthead: Pair<Number, Number>, display: Pair<Number, Number>, headline: Pair<Number, Number>, title: Pair<Number, Number>,
            body: Pair<Number, Number>, label: Pair<Number, Number>, labelSmall: Pair<Number, Number>, caption: Pair<Number, Number>,
            mono: Pair<Number, Number>,
        ) = NagType(
            display = serif(BodoniDisplay, display, FontWeight.Medium),
            headline = serif(BodoniHeadline, headline, FontWeight.SemiBold),
            title = sans(title, SemiBoldPlus),
            body = sans(body, FontWeight.Normal),
            bodyStrong = sans(body, FontWeight.SemiBold),
            label = sans(label, FontWeight.SemiBold),
            labelSmall = sans(labelSmall, FontWeight.Medium),
            caption = sans(caption, FontWeight.Normal),
            mono = TextStyle(
                fontFamily = PlexMono,
                fontWeight = FontWeight.Normal,
                fontSize = mono.first.toFloat().sp,
                lineHeight = mono.second.toFloat().sp,
            ),
            masthead = serif(BodoniMasthead, masthead, FontWeight.Medium, letterSpacing = (-0.01).em),
            overline = sans(caption, SemiBoldPlus, letterSpacing = 0.06.em),
        )

        private fun serif(family: FontFamily, size: Pair<Number, Number>, weight: FontWeight, letterSpacing: TextUnit = TextUnit.Unspecified) = TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontStyle = FontStyle.Italic,
            fontSize = size.first.toFloat().sp,
            lineHeight = size.second.toFloat().sp,
            letterSpacing = letterSpacing,
        )

        private fun sans(size: Pair<Number, Number>, weight: FontWeight, letterSpacing: TextUnit = TextUnit.Unspecified) = TextStyle(
            fontFamily = Bricolage,
            fontWeight = weight,
            fontSize = size.first.toFloat().sp,
            lineHeight = size.second.toFloat().sp,
            letterSpacing = letterSpacing,
        )
    }
}
