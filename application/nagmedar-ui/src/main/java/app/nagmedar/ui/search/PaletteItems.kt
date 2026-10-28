package app.nagmedar.ui.search

import android.content.res.Resources
import androidx.compose.ui.graphics.vector.ImageVector
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.ThemeMode
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/*
 * What the command palette offers in the app (S-305): the shortcuts the shell can run, every
 * screen, the appearance settings, and the library's videos and tracks.
 */

/** The shortcuts [available] now, with their menu and keys; the palette's own shortcut is left out. */
fun commandItems(res: Resources, available: (ShortcutId) -> Boolean, perform: (ShortcutId) -> Unit): List<PaletteItem> =
    ShortcutRegistry.shortcuts.filter { it.id != ShortcutId.CommandPalette && available(it.id) }.map { shortcut ->
        PaletteItem(
            id = "command:${shortcut.id.name}",
            kind = PaletteKind.Command,
            label = res.getString(shortcut.label),
            detail = res.getString(shortcut.group.title),
            shortcut = shortcut.chord?.label(res),
            icon = groupIcon(shortcut.group),
            run = { perform(shortcut.id) },
        )
    }

private fun groupIcon(group: ShortcutGroup): ImageVector = when (group) {
    ShortcutGroup.Media -> NagIcons.Folder
    ShortcutGroup.Playback -> NagIcons.Play
    ShortcutGroup.Audio -> NagIcons.Volume
    ShortcutGroup.Tools -> NagIcons.TvLab
    ShortcutGroup.View -> NagIcons.Grid
    ShortcutGroup.Help -> NagIcons.Keyboard
}

/** Every screen of the shell, with its section. */
fun screenItems(res: Resources, navigate: (Destination) -> Unit): List<PaletteItem> =
    Destination.entries.filter { it != Destination.More }.map { destination ->
        PaletteItem(
            id = "screen:${destination.route}",
            kind = PaletteKind.Screen,
            label = res.getString(destination.label),
            detail = destination.section.title?.let { res.getString(it) }.orEmpty(),
            icon = destination.icon,
            run = { navigate(destination) },
        )
    }

/**
 * The settings that take effect at once: theme, accent ([wallpaperAccent]: the wallpaper's colour
 * is offered, Android 12+) and density, each value its own item ("Tema: Koyu").
 */
fun settingItems(res: Resources, prefs: NagmedarPreferences, wallpaperAccent: Boolean): List<PaletteItem> {
    val appearance = res.getString(R.string.nag_settings_appearance)
    fun item(id: String, setting: Int, value: Int, run: () -> Unit) = PaletteItem(
        id = "setting:$id",
        kind = PaletteKind.Setting,
        label = res.getString(R.string.nag_palette_setting, res.getString(setting), res.getString(value)),
        detail = appearance,
        icon = NagIcons.Settings,
        run = run,
    )
    val themes = listOf(
        ThemeMode.System to R.string.nag_theme_system,
        ThemeMode.Light to R.string.nag_theme_light,
        ThemeMode.Dark to R.string.nag_theme_dark,
        ThemeMode.Oled to R.string.nag_theme_oled,
    ).map { (mode, label) -> item("theme.${mode.name}", R.string.nag_settings_theme, label) { prefs.updateThemeMode(mode) } }
    val accents = listOfNotNull(
        (Accent.System to R.string.nag_accent_system).takeIf { wallpaperAccent },
        Accent.Blue to R.string.nag_accent_blue,
        Accent.Amber to R.string.nag_accent_amber,
        Accent.Green to R.string.nag_accent_green,
        Accent.Coral to R.string.nag_accent_coral,
    ).map { (accent, label) -> item("accent.${accent.name}", R.string.nag_settings_accent, label) { prefs.updateAccent(accent) } }
    val densities = listOf(
        DensityPreference.Auto to R.string.nag_density_auto,
        DensityPreference.Touch to R.string.nag_density_touch,
        DensityPreference.Pointer to R.string.nag_density_pointer,
    ).map { (density, label) -> item("density.${density.name}", R.string.nag_settings_density, label) { prefs.updateDensity(density) } }
    return themes + accents + densities
}

/** The library's tracks, with their artist. */
fun mediaItems(res: Resources, media: List<MediaWrapper>, play: (MediaWrapper) -> Unit): List<PaletteItem> = media.map { item ->
    PaletteItem(
        id = "media:${item.uri}",
        kind = PaletteKind.Media,
        label = item.title ?: item.uri?.lastPathSegment.orEmpty(),
        detail = listOfNotNull(res.getString(R.string.nag_palette_track), item.artistName?.takeIf { it.isNotBlank() }).joinToString(" · "),
        icon = NagIcons.Music,
        run = { play(item) },
    )
}
