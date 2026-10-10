package app.nagmedar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.R

/** IBM Plex Sans for the interface, IBM Plex Mono for timecodes, formats and key caps (SIL OFL 1.1). */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Text styles. Two sets: pointer (DeX, mouse) is denser, touch is larger. Nothing is below 11 sp. */
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
) {
    companion object {
        val Pointer = build(display = 22, headline = 18, title = 16, body = 13, label = 12.5f, labelSmall = 11.5f, caption = 11, mono = 11)
        val Touch = build(display = 26, headline = 20, title = 17, body = 15, label = 14, labelSmall = 13, caption = 12, mono = 12)

        fun forMode(mode: InputMode) = if (mode == InputMode.Pointer) Pointer else Touch

        private fun build(display: Number, headline: Number, title: Number, body: Number, label: Number,
                          labelSmall: Number, caption: Number, mono: Number) = NagType(
            display = sans(display, FontWeight.SemiBold, lineHeight = display.toFloat() * 1.25f),
            headline = sans(headline, FontWeight.SemiBold, lineHeight = headline.toFloat() * 1.3f),
            title = sans(title, FontWeight.SemiBold, lineHeight = title.toFloat() * 1.35f),
            body = sans(body, FontWeight.Normal, lineHeight = body.toFloat() * 1.4f),
            bodyStrong = sans(body, FontWeight.Medium, lineHeight = body.toFloat() * 1.4f),
            label = sans(label, FontWeight.Medium, lineHeight = label.toFloat() * 1.3f),
            labelSmall = sans(labelSmall, FontWeight.Normal, lineHeight = labelSmall.toFloat() * 1.3f),
            caption = sans(caption, FontWeight.Normal, lineHeight = caption.toFloat() * 1.3f),
            mono = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = mono.toFloat().sp,
                lineHeight = (mono.toFloat() * 1.3f).sp),
        )

        private fun sans(size: Number, weight: FontWeight, lineHeight: Float) = TextStyle(
            fontFamily = PlexSans,
            fontWeight = weight,
            fontSize = size.toFloat().sp,
            lineHeight = lineHeight.sp,
            letterSpacing = TextUnit.Unspecified,
        )
    }
}
