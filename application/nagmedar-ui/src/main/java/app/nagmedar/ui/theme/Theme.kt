package app.nagmedar.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalNagColors = staticCompositionLocalOf { NagPalette.dark(Accent.Blue) }
val LocalNagType = staticCompositionLocalOf { NagType.Pointer }
val LocalNagDimens = staticCompositionLocalOf { NagDimens.Pointer }
val LocalInputMode = staticCompositionLocalOf { InputMode.Pointer }
val LocalNagMotion = staticCompositionLocalOf { NagMotion.Full }

/** The window is a tablet's or DeX's (600 dp and wider): larger text, chips that wrap. */
val LocalLargeWindow = staticCompositionLocalOf { true }

/** Token access for composables: `NagTheme.colors.text`, `NagTheme.dimens.rowHeight`, … */
object NagTheme {
    val colors: NagColors
        @Composable @ReadOnlyComposable get() = LocalNagColors.current
    val type: NagType
        @Composable @ReadOnlyComposable get() = LocalNagType.current
    val dimens: NagDimens
        @Composable @ReadOnlyComposable get() = LocalNagDimens.current
    val inputMode: InputMode
        @Composable @ReadOnlyComposable get() = LocalInputMode.current
    val motion: NagMotion
        @Composable @ReadOnlyComposable get() = LocalNagMotion.current

    /** A tablet's or DeX's window (600 dp and wider), as the theme sized its text for. */
    val largeWindow: Boolean
        @Composable @ReadOnlyComposable get() = LocalLargeWindow.current
}

/**
 * Nağmedar theme: light, dark and OLED black palettes, four accents and the wallpaper's (S-204),
 * two densities and the motion tokens. Material 3 components read a matching colour scheme, so
 * they blend with the custom ones. [reduceMotion] null follows the system's "Remove animations".
 *
 * As Desktop Kit 0.2.0 has it, the window decides the last step: on tablet and DeX windows
 * (600 dp and wider, the rail and sidebar layouts) every text style is [largeWindowTextStep] sp
 * larger; on a phone's window the touch sizes are a little tighter ([compactSizes]: a 64 dp
 * bottom bar, 52 dp rows, targets still 48 dp).
 *
 * As Desktop Kit 1.0 has it, a new accent or theme cross-fades over [colorChangeMillis] (150 ms;
 * glass tints follow, as they are made from these colours), at once with reduced motion; and the
 * window's [MotionTier] is provided ([LocalMotionTier]: springs on a phone's window).
 */
@Composable
fun NagmedarTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    inputMode: InputMode = InputMode.Pointer,
    reduceMotion: Boolean? = null,
    largeWindowTextStep: Float = LARGE_WINDOW_TEXT_STEP,
    compactSizes: Boolean = true,
    colorChangeMillis: Int = NagMotion.COLOR_CHANGE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val seed = if (accent == Accent.System) systemAccentSeed() else null
    val target = remember(themeMode, systemDark, accent, seed) { NagPalette.colors(themeMode, systemDark, accent, seed) }
    val motion = NagMotion.of(reduceMotion ?: rememberSystemReducedMotion())
    val colors = animatePalette(target, if (motion.reduced) 0 else colorChangeMillis)
    val largeWindow = LocalConfiguration.current.screenWidthDp >= LARGE_WINDOW_DP
    val type = remember(inputMode, largeWindow, largeWindowTextStep) {
        NagType.forMode(inputMode).let { if (largeWindow) it.grow(largeWindowTextStep) else it }
    }
    val dimens = NagDimens.forMode(inputMode).let { if (compactSizes && !largeWindow) it.forCompactWindow() else it }
    val scheme = remember(colors) { colors.toMaterialScheme() }
    val selection = remember(colors) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.35f)) }
    val tier = when {
        motion.reduced -> MotionTier.Reduced
        largeWindow -> MotionTier.Timed
        else -> MotionTier.Gesture
    }
    CompositionLocalProvider(
        LocalNagColors provides colors,
        LocalNagType provides type,
        LocalNagDimens provides dimens,
        LocalInputMode provides inputMode,
        LocalNagMotion provides motion,
        LocalLargeWindow provides largeWindow,
        LocalMotionTier provides tier,
    ) {
        MaterialTheme(colorScheme = scheme, typography = type.toMaterialTypography()) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}

/**
 * [target], cross-faded from the previous palette over [durationMillis] whenever it changes
 * (Desktop Kit 1.0). The first composition shows [target] at once; a change that interrupts
 * another starts from what is on screen.
 */
@Composable
internal fun animatePalette(target: NagColors, durationMillis: Int): NagColors {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, durationMillis) {
        if (target == to) return@LaunchedEffect
        from = lerpColors(from, to, progress.value)
        to = target
        if (durationMillis <= 0) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = NagMotion.Standard))
        }
    }
    val t = progress.value
    return if (t >= 1f || from == to) to else lerpColors(from, to, t)
}

/** Every colour token of [a] moved [t] of the way to [b]; [NagColors.isDark] is [b]'s. */
fun lerpColors(a: NagColors, b: NagColors, t: Float): NagColors = when {
    t >= 1f -> b
    t <= 0f -> a.copy(isDark = b.isDark)
    else -> NagColors(
        isDark = b.isDark,
        background = lerp(a.background, b.background, t),
        chrome = lerp(a.chrome, b.chrome, t),
        surface = lerp(a.surface, b.surface, t),
        surfaceRaised = lerp(a.surfaceRaised, b.surfaceRaised, t),
        hover = lerp(a.hover, b.hover, t),
        pressed = lerp(a.pressed, b.pressed, t),
        border = lerp(a.border, b.border, t),
        borderStrong = lerp(a.borderStrong, b.borderStrong, t),
        text = lerp(a.text, b.text, t),
        textSecondary = lerp(a.textSecondary, b.textSecondary, t),
        textTertiary = lerp(a.textTertiary, b.textTertiary, t),
        accent = lerp(a.accent, b.accent, t),
        onAccent = lerp(a.onAccent, b.onAccent, t),
        accentText = lerp(a.accentText, b.accentText, t),
        selection = lerp(a.selection, b.selection, t),
        onSelection = lerp(a.onSelection, b.onSelection, t),
        focusRing = lerp(a.focusRing, b.focusRing, t),
        success = lerp(a.success, b.success, t),
        warning = lerp(a.warning, b.warning, t),
        danger = lerp(a.danger, b.danger, t),
        brandBlue = lerp(a.brandBlue, b.brandBlue, t),
        brandCoral = lerp(a.brandCoral, b.brandCoral, t),
        scrim = lerp(a.scrim, b.scrim, t),
        inverseSurface = lerp(a.inverseSurface, b.inverseSurface, t),
        onInverseSurface = lerp(a.onInverseSurface, b.onInverseSurface, t),
        inverseAccent = lerp(a.inverseAccent, b.inverseAccent, t),
    )
}

/**
 * These colours with the accent of [tones] in their place: the accent fills, its text, the
 * selection and the focus ring, as [NagPalette] makes them for this palette (light, dark or OLED).
 */
fun NagColors.withAccent(tones: AccentTones): NagColors = if (!isDark) {
    copy(accent = tones.deep, accentText = tones.deep, selection = tones.lightSelection, focusRing = tones.deep, inverseAccent = tones.base)
} else {
    copy(
        accent = tones.base,
        accentText = tones.base,
        selection = if (background == Color.Black) tones.oledSelection else tones.darkSelection,
        onSelection = tones.onDarkSelection,
        focusRing = tones.base,
        inverseAccent = tones.darkInverse,
    )
}

/**
 * Desktop Kit 1.0's artwork accent, for the player's surfaces only: below this point the accent
 * (and the glass tint made from it) is the one of [seed], the playing track's artwork colour
 * ([app.nagmedar.ui.music.rememberArtworkColor]), tuned to keep every contrast
 * ([AccentTones.fromSeed]). [seed] null keeps the theme's accent.
 *
 * 1.0's review: the palette below changes once, when a new track's tones are ready, never on every
 * frame of a cross-fade (a static local recomposes all it holds, the mini player in the middle of
 * its swipe and the whole full player). The surfaces that show the accent largely (the timeline,
 * the progress line, the play button) cross-fade to it themselves while drawing ([animatedAccent]).
 */
@Composable
fun ArtworkAccent(seed: Color?, content: @Composable () -> Unit) {
    val base = LocalNagColors.current
    // the tones are tuned against all three palettes: away from the main thread
    val tones by produceState<AccentTones?>(null, seed) {
        value = seed?.let { color -> withContext(Dispatchers.Default) { AccentTones.fromSeed(color) } }
    }
    val colors = remember(base, tones) { tones?.let(base::withAccent) ?: base }
    CompositionLocalProvider(LocalNagColors provides colors, content = content)
}

/**
 * [color], an accent token, as it is on screen while it cross-fades to a new accent (a new track's
 * artwork, [ArtworkAccent]; another accent chosen) over [NagMotion.COLOR_CHANGE] ms, at once with
 * reduced motion. Read its value while drawing (`drawBehind { drawRect(fill.value) }`): the fade
 * then redraws the surface on each frame without recomposing anything.
 */
@Composable
fun animatedAccent(color: Color, label: String = "accent"): State<Color> =
    animateColorAsState(color, NagTheme.motion.color(), label = label)

private fun NagColors.toMaterialScheme(): ColorScheme = if (isDark) darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = selection,
    onPrimaryContainer = onSelection,
    secondary = brandCoral,
    onSecondary = onAccent,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = hover,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    outline = borderStrong,
    outlineVariant = border,
    error = danger,
    scrim = scrim,
) else lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = selection,
    onPrimaryContainer = onSelection,
    secondary = brandCoral,
    onSecondary = onAccent,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = hover,
    onSurfaceVariant = textSecondary,
    surfaceContainer = surfaceRaised,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = hover,
    surfaceContainerLow = surface,
    surfaceContainerLowest = background,
    outline = borderStrong,
    outlineVariant = border,
    error = danger,
    scrim = scrim,
)

private fun NagType.toMaterialTypography() = Typography(
    displaySmall = display,
    headlineSmall = headline,
    titleLarge = headline,
    titleMedium = title,
    titleSmall = bodyStrong,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = labelSmall,
    labelLarge = label,
    labelMedium = labelSmall,
    labelSmall = caption,
)

/** Windows this wide and wider are tablets' and DeX's (the shell's rail and sidebar layouts, WidthClass.Medium). */
const val LARGE_WINDOW_DP = 600

/** The sp every text style grows by on those windows. */
const val LARGE_WINDOW_TEXT_STEP = 1f

/**
 * S-204: the wallpaper's accent colour (Android 12+), the seed of [Accent.System]; null on older
 * systems. Read again when the configuration changes, as it does when the wallpaper colours do.
 */
@Composable
fun systemAccentSeed(): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { Color(context.getColor(android.R.color.system_accent1_500)) }
}
