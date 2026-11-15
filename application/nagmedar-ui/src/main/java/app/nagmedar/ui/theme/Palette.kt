package app.nagmedar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Appearance chosen in Settings → Appearance. */
enum class ThemeMode { System, Light, Dark, Oled }

/**
 * Accent colours offered by the design; blue is the brand colour. 1.1.0 (Ebru): the four inks are tuned for the Gece,
 * Su and Kara grounds (each tone found as [AccentTones.fromSeed] finds it, from the 1.0 colours as seeds).
 */
enum class Accent(val base: Color) {
    Blue(Color(0xFF84A9F8)),
    Amber(Color(0xFFD8A02C)),
    Green(Color(0xFF48C58C)),
    Coral(Color(0xFFFF7F73)),

    /**
     * S-204: the wallpaper's colour (Android 12+), turned into tones that keep every contrast
     * ([AccentTones.fromSeed]); blue where the system has no such colour. [base] is that blue.
     */
    System(Color(0xFF84A9F8)),

    /**
     * 1.1.0 (Ebru): the ink of the album that plays, from its cover's colour ([app.nagmedar.ui.music.rememberArtworkColor])
     * tuned as the wallpaper's is ([AccentTones.fromSeed]); blue while nothing with a coloured cover plays.
     */
    Album(Color(0xFF84A9F8)),
}

/**
 * Colour tokens. Every text/background pair used by the components keeps a contrast of at
 * least 4.5:1 in the resting and hover states (checked by PaletteContrastTest).
 */
@Immutable
data class NagColors(
    val isDark: Boolean,
    /** Window background behind the content. */
    val background: Color,
    /** Caption bar, menu bar, navigation surfaces. */
    val chrome: Color,
    /** Cards, panels, list containers. */
    val surface: Color,
    /** Menus, tooltips, dialogs. */
    val surfaceRaised: Color,
    val hover: Color,
    val pressed: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Accent used as a fill (selected indicator, primary button). */
    val accent: Color,
    val onAccent: Color,
    /** Accent used for text and icons on [background]. */
    val accentText: Color,
    /** Selected row / navigation item background. */
    val selection: Color,
    val onSelection: Color,
    val focusRing: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Logo halves; the light theme uses deeper tones. */
    val brandBlue: Color,
    val brandCoral: Color,
    val scrim: Color,
    /** Undo bar and other surfaces that stand out against the theme: light on the dark themes, dark on the light one. */
    val inverseSurface: Color,
    val onInverseSurface: Color,
    /** Accent for text on [inverseSurface] (the undo bar's "Undo"). */
    val inverseAccent: Color,
    /**
     * 1.0.8 (Desktop Kit 2.8.0, the design audit's high finding 5): the edge of an input at rest (a text field, a switch's
     * off track): at least 3:1 against the background, the chrome and the surfaces, so the control can be found where it
     * is (WCAG 1.4.11). [borderStrong] stays for the edges that only part things (buttons, panels, menus).
     */
    val controlBorder: Color = borderStrong,
    /** 1.0.8: darkens artwork under what is drawn on it (a cover's check ring). */
    val overlayScrim: Color = Color(0x59000000),
    /** 1.0.8: icons and edges drawn on artwork, over [overlayScrim]. */
    val onOverlay: Color = Color(0xFFFFFFFF),
)

object NagPalette {

    /** [systemSeed]: the wallpaper colour behind [Accent.System], the playing album's cover colour behind [Accent.Album]. */
    fun colors(mode: ThemeMode, systemDark: Boolean, accent: Accent, systemSeed: Color? = null): NagColors =
        colors(mode, systemDark, AccentTones.of(accent, systemSeed))

    /** The palette of [mode] with [tones] as its accent. */
    fun colors(mode: ThemeMode, systemDark: Boolean, tones: AccentTones): NagColors {
        return when (mode) {
            ThemeMode.Light -> light(tones)
            ThemeMode.Dark -> dark(tones)
            ThemeMode.Oled -> oled(tones)
            ThemeMode.System -> if (systemDark) dark(tones) else light(tones)
        }
    }

    fun dark(accent: Accent) = dark(AccentTones.of(accent))

    fun oled(accent: Accent) = oled(AccentTones.of(accent))

    fun light(accent: Accent) = light(AccentTones.of(accent))

    /**
     * 1.1.0 (Ebru): Gece, a night-blue soot under the inks; the chrome a step above the page, the panels and the raised
     * surfaces further up, each text at 4.5:1 on all of them under the pointer too.
     */
    fun dark(tones: AccentTones) = NagColors(
        isDark = true,
        background = Color(0xFF0E1014),
        chrome = Color(0xFF121519),
        surface = Color(0xFF15181E),
        surfaceRaised = Color(0xFF1D2129),
        hover = Color(0xFF23272F),
        // 1.0.8 (Desktop Kit 2.8.0): a step past hover, so a press shows under the pointer
        pressed = Color(0xFF2D323B),
        border = Color(0xFF1F232A),
        borderStrong = Color(0xFF2B3038),
        text = Color(0xFFEEF0F2),
        textSecondary = Color(0xFFB0B6C0),
        textTertiary = Color(0xFF8E96A2),
        accent = tones.base,
        onAccent = Color(0xFF14110C),
        accentText = tones.base,
        selection = tones.darkSelection,
        onSelection = tones.onDarkSelection,
        focusRing = tones.base,
        success = Color(0xFF6CD39C),
        warning = Color(0xFFF2B84B),
        danger = Color(0xFFFF7D6E),
        brandBlue = Color(0xFF7FA8F0),
        brandCoral = Color(0xFFF27C62),
        scrim = Color(0x99000000),
        inverseSurface = Color(0xFFEEF0F2),
        onInverseSurface = Color(0xFF14181C),
        inverseAccent = tones.darkInverse,
        controlBorder = Color(0xFF6E7683),
    )

    /** 1.1.0 (Ebru): Kara, the inks on black; the panels barely lifted from it. */
    fun oled(tones: AccentTones) = dark(tones).copy(
        background = Color(0xFF000000),
        chrome = Color(0xFF000000),
        surface = Color(0xFF0A0B0D),
        surfaceRaised = Color(0xFF15171B),
        hover = Color(0xFF1B1E23),
        pressed = Color(0xFF262A30),
        border = Color(0xFF16181C),
        borderStrong = Color(0xFF22252B),
        selection = tones.oledSelection,
        scrim = Color(0xB3000000),
    )

    /**
     * 1.1.0 (Ebru): Su, a broken white leaning to water, the page a shade under the panels as marbling paper is under
     * its ink; the raised surfaces white.
     */
    fun light(tones: AccentTones) = NagColors(
        isDark = false,
        background = Color(0xFFEEF0EC),
        chrome = Color(0xFFF4F5F2),
        surface = Color(0xFFF7F8F5),
        surfaceRaised = Color(0xFFFFFFFF),
        // 1.0.8 (Desktop Kit 2.8.0): hover shows on the page, a press a step further
        hover = Color(0xFFE2E5DF),
        pressed = Color(0xFFD5D9D2),
        border = Color(0xFFDADED7),
        borderStrong = Color(0xFFCBD0C8),
        text = Color(0xFF151C19),
        textSecondary = Color(0xFF46514C),
        textTertiary = Color(0xFF58625D),
        accent = tones.deep,
        onAccent = Color(0xFFFFFFFF),
        accentText = tones.deep,
        selection = tones.lightSelection,
        onSelection = Color(0xFF101613),
        focusRing = tones.deep,
        success = Color(0xFF146F47),
        warning = Color(0xFF875811),
        danger = Color(0xFFB1372A),
        brandBlue = Color(0xFF3F68C4),
        brandCoral = Color(0xFFC24A33),
        scrim = Color(0x66151C19),
        inverseSurface = Color(0xFF1D2421),
        onInverseSurface = Color(0xFFEEF0EC),
        inverseAccent = tones.base,
        controlBorder = Color(0xFF78827C),
    )

    /** 1.0.8 (Desktop Kit 2.8.0): the check on a colour swatch (the accent chooser): the swatches are all light enough for it. */
    val onSwatch: Color = Color(0xFF0E1420)
}

/**
 * 1.1.0 (Ebru): the danger colour that reads on [NagColors.inverseSurface] (a notice's warning sign and edge): the
 * light palette's deep red on the dark themes' light card, the dark palette's coral on the light theme's dark one.
 */
fun NagColors.inverseDanger(): Color = if (isDark) Color(0xFFB1372A) else Color(0xFFFF7D6E)

/** CSS `color-mix(in srgb, a fraction, b)`: mixes in gamma-encoded sRGB like the mockups. */
fun mixSrgb(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red * fraction + b.red * (1 - fraction),
    green = a.green * fraction + b.green * (1 - fraction),
    blue = a.blue * fraction + b.blue * (1 - fraction),
    alpha = 1f,
)
