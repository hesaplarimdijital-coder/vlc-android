package app.nagmedar.ui.theme

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

val LocalNagColors = staticCompositionLocalOf { NagPalette.dark(Accent.Blue) }
val LocalNagType = staticCompositionLocalOf { NagType.Pointer }
val LocalNagDimens = staticCompositionLocalOf { NagDimens.Pointer }
val LocalInputMode = staticCompositionLocalOf { InputMode.Pointer }

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
}

/**
 * Nağmedar theme: light, dark and OLED black palettes, four accents and two densities.
 * Material 3 components read a matching colour scheme, so they blend with the custom ones.
 */
@Composable
fun NagmedarTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Blue,
    inputMode: InputMode = InputMode.Pointer,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = remember(themeMode, systemDark, accent) { NagPalette.colors(themeMode, systemDark, accent) }
    val type = NagType.forMode(inputMode)
    val dimens = NagDimens.forMode(inputMode)
    val scheme = remember(colors) { colors.toMaterialScheme() }
    val selection = remember(colors) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.35f)) }
    CompositionLocalProvider(
        LocalNagColors provides colors,
        LocalNagType provides type,
        LocalNagDimens provides dimens,
        LocalInputMode provides inputMode,
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
