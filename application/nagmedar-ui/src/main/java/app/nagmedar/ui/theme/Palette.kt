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
)

object NagPalette {

    fun colors(mode: ThemeMode, systemDark: Boolean, accent: Accent): NagColors = when (mode) {
        ThemeMode.Light -> light(accent)
        ThemeMode.Dark -> dark(accent)
        ThemeMode.Oled -> oled(accent)
        ThemeMode.System -> if (systemDark) dark(accent) else light(accent)
    }

    fun dark(accent: Accent) = NagColors(
        isDark = true,
        background = Color(0xFF111418),
        chrome = Color(0xFF0C0F12),
        surface = Color(0xFF171B20),
        surfaceRaised = Color(0xFF1F252C),
        hover = Color(0xFF262C34),
        pressed = Color(0xFF2A3038),
        border = Color(0xFF20262D),
        borderStrong = Color(0xFF2A3038),
        text = Color(0xFFE6E9ED),
        textSecondary = Color(0xFFC3CAD3),
        textTertiary = Color(0xFF959FAB),
        accent = accent.base,
        onAccent = Color(0xFF0E1420),
        accentText = accent.base,
        selection = darkSelection(accent),
        onSelection = onDarkSelection(accent),
        focusRing = accent.base,
        success = Color(0xFF59D49A),
        warning = Color(0xFFF2B84B),
        danger = Color(0xFFFF7A6E),
        brandBlue = Color(0xFF8AB0FF),
        brandCoral = Color(0xFFFF7A6E),
        scrim = Color(0x99000000),
    )

    fun oled(accent: Accent) = dark(accent).copy(
        background = Color(0xFF000000),
        chrome = Color(0xFF000000),
        surface = Color(0xFF0A0C0F),
        surfaceRaised = Color(0xFF161A1F),
        hover = Color(0xFF1C2127),
        pressed = Color(0xFF242A31),
        border = Color(0xFF1A1F25),
        borderStrong = Color(0xFF262C34),
        selection = oledSelection(accent),
        scrim = Color(0xB3000000),
    )

    fun light(accent: Accent): NagColors {
        val deep = mixSrgb(accent.base, Color.Black, 0.55f)
        return NagColors(
            isDark = false,
            background = Color(0xFFFAFBFC),
            chrome = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            hover = Color(0xFFEDF0F4),
            pressed = Color(0xFFE2E5EA),
            border = Color(0xFFE2E5EA),
            borderStrong = Color(0xFFD6DAE0),
            text = Color(0xFF12161B),
            textSecondary = Color(0xFF4A5360),
            textTertiary = Color(0xFF58626D),
            accent = deep,
            onAccent = Color(0xFFFFFFFF),
            accentText = deep,
            selection = mixSrgb(accent.base, Color.White, 0.22f),
            onSelection = Color(0xFF0E1420),
            focusRing = deep,
            success = Color(0xFF187A4F),
            warning = Color(0xFF8A5A12),
            danger = Color(0xFFB4382B),
            brandBlue = Color(0xFF4E78D8),
            brandCoral = Color(0xFFE0574B),
            scrim = Color(0x6612161B),
        )
    }

    private fun darkSelection(accent: Accent) = when (accent) {
        Accent.Blue -> Color(0xFF1C2A49)
        Accent.Amber -> Color(0xFF33290F)
        Accent.Green -> Color(0xFF12301F)
        Accent.Coral -> Color(0xFF3A1E1B)
    }

    private fun oledSelection(accent: Accent) = when (accent) {
        Accent.Blue -> Color(0xFF142039)
        Accent.Amber -> Color(0xFF2C230F)
        Accent.Green -> Color(0xFF102B1F)
        Accent.Coral -> Color(0xFF2E1816)
    }

    private fun onDarkSelection(accent: Accent) = when (accent) {
        Accent.Blue -> Color(0xFFDCE6FF)
        Accent.Amber -> Color(0xFFF8E3BD)
        Accent.Green -> Color(0xFFCFF3E2)
        Accent.Coral -> Color(0xFFFFD9D4)
    }
}

/** CSS `color-mix(in srgb, a fraction, b)`: mixes in gamma-encoded sRGB like the mockups. */
fun mixSrgb(a: Color, b: Color, fraction: Float): Color = Color(
    red = a.red * fraction + b.red * (1 - fraction),
    green = a.green * fraction + b.green * (1 - fraction),
    blue = a.blue * fraction + b.blue * (1 - fraction),
    alpha = 1f,
)
