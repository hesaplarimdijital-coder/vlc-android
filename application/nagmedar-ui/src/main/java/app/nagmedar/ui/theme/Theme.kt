package app.nagmedar.ui.theme

import android.os.Build
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

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
 */
@Composable
fun NagmedarTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    inputMode: InputMode = InputMode.Pointer,
    reduceMotion: Boolean? = null,
    largeWindowTextStep: Float = LARGE_WINDOW_TEXT_STEP,
    compactSizes: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val seed = if (accent == Accent.System) systemAccentSeed() else null
    val colors = remember(themeMode, systemDark, accent, seed) { NagPalette.colors(themeMode, systemDark, accent, seed) }
    val largeWindow = LocalConfiguration.current.screenWidthDp >= LARGE_WINDOW_DP
    val type = remember(inputMode, largeWindow, largeWindowTextStep) {
        NagType.forMode(inputMode).let { if (largeWindow) it.grow(largeWindowTextStep) else it }
    }
    val dimens = NagDimens.forMode(inputMode).let { if (compactSizes && !largeWindow) it.forCompactWindow() else it }
    val scheme = remember(colors) { colors.toMaterialScheme() }
    val selection = remember(colors) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.35f)) }
    val motion = NagMotion.of(reduceMotion ?: rememberSystemReducedMotion())
    CompositionLocalProvider(
        LocalNagColors provides colors,
        LocalNagType provides type,
        LocalNagDimens provides dimens,
        LocalInputMode provides inputMode,
        LocalNagMotion provides motion,
        LocalLargeWindow provides largeWindow,
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
