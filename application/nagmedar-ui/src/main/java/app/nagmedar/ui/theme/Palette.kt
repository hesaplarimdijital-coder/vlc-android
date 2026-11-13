package app.nagmedar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Appearance chosen in Settings → Appearance. */
enum class ThemeMode { System, Light, Dark, Oled }

/** Accent colours offered by the design; blue is the brand colour. */
enum class Accent(val base: Color) {
    Blue(Color(0xFF8AB0FF)),
    Amber(Color(0xFFF2B84B)),
    Green(Color(0xFF59D49A)),
    Coral(Color(0xFFFF7A6E)),

    /**
     * S-204: the wallpaper's colour (Android 12+), turned into tones that keep every contrast
     * ([AccentTones.fromSeed]); blue where the system has no such colour. [base] is that blue.
     */
    System(Color(0xFF8AB0FF)),
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

    /** [systemSeed]: the wallpaper colour behind [Accent.System]. */
    fun colors(mode: ThemeMode, systemDark: Boolean, accent: Accent, systemSeed: Color? = null): NagColors {
        val tones = AccentTones.of(accent, systemSeed)
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

    fun dark(tones: AccentTones) = NagColors(
        isDark = true,
        background = Color(0xFF111418),
        chrome = Color(0xFF0C0F12),
        surface = Color(0xFF171B20),
        surfaceRaised = Color(0xFF1F252C),
        hover = Color(0xFF262C34),
        // 1.0.8 (Desktop Kit 2.8.0): a step past hover, so a press shows under the pointer (1.16:1 to hover; 1.06:1 before)
        pressed = Color(0xFF30363F),
        border = Color(0xFF20262D),
        borderStrong = Color(0xFF2A3038),
        text = Color(0xFFE6E9ED),
        textSecondary = Color(0xFFC3CAD3),
        textTertiary = Color(0xFF959FAB),
        accent = tones.base,
        onAccent = Color(0xFF0E1420),
        accentText = tones.base,
        selection = tones.darkSelection,
        onSelection = tones.onDarkSelection,
        focusRing = tones.base,
        success = Color(0xFF59D49A),
        warning = Color(0xFFF2B84B),
        danger = Color(0xFFFF7A6E),
        brandBlue = Color(0xFF8AB0FF),
        brandCoral = Color(0xFFFF7A6E),
        scrim = Color(0x99000000),
        inverseSurface = Color(0xFFE6E9ED),
        onInverseSurface = Color(0xFF14181C),
        inverseAccent = tones.darkInverse,
        controlBorder = Color(0xFF6A7480),
    )

    fun oled(tones: AccentTones) = dark(tones).copy(
        background = Color(0xFF000000),
        chrome = Color(0xFF000000),
        surface = Color(0xFF0A0C0F),
        surfaceRaised = Color(0xFF161A1F),
        hover = Color(0xFF1C2127),
        pressed = Color(0xFF2A3038),
        border = Color(0xFF1A1F25),
        borderStrong = Color(0xFF262C34),
        selection = tones.oledSelection,
        scrim = Color(0xB3000000),
    )

    fun light(tones: AccentTones) = NagColors(
        isDark = false,
        background = Color(0xFFFAFBFC),
        chrome = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFFFFFFF),
        // 1.0.8 (Desktop Kit 2.8.0): hover shows on the page (1.14:1 to it; 1.10:1 before), a press a step further
        hover = Color(0xFFE9EDF2),
        pressed = Color(0xFFDADEE4),
        border = Color(0xFFE2E5EA),
        borderStrong = Color(0xFFD6DAE0),
        text = Color(0xFF12161B),
        textSecondary = Color(0xFF4A5360),
        textTertiary = Color(0xFF58626D),
        accent = tones.deep,
        onAccent = Color(0xFFFFFFFF),
        accentText = tones.deep,
        selection = tones.lightSelection,
        onSelection = Color(0xFF0E1420),
        focusRing = tones.deep,
        success = Color(0xFF187A4F),
        warning = Color(0xFF8A5A12),
        danger = Color(0xFFB4382B),
        brandBlue = Color(0xFF4E78D8),
        brandCoral = Color(0xFFE0574B),
        scrim = Color(0x6612161B),
        inverseSurface = Color(0xFF1F252C),
        onInverseSurface = Color(0xFFE6E9ED),
        inverseAccent = tones.base,
        controlBorder = Color(0xFF7E8794),
    )

    /** 1.0.8 (Desktop Kit 2.8.0): the check on a colour swatch (the accent chooser): the swatches are all light enough for it. */
    val onSwatch: Color = Color(0xFF0E1420)
}

/** CSS `color-mix(in srgb, a fraction, b)`: mixes in gamma-encoded sRGB like the mockups. */
fun mixSrgb(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red * fraction + b.red * (1 - fraction),
    green = a.green * fraction + b.green * (1 - fraction),
    blue = a.blue * fraction + b.blue * (1 - fraction),
    alpha = 1f,
)
