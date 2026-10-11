package app.nagmedar.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.components.TooltipDelay
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutOverrides
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.shell.PaneWidths
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.ThemeMode
import org.videolan.tools.NAGMEDAR_FIRST_RUN_DONE
import org.videolan.tools.NAGMEDAR_LAUNCH_NEW_UI
import org.videolan.tools.NAGMEDAR_MIDDLE_CLICK
import org.videolan.tools.NAGMEDAR_MIDDLE_PLAY_PAUSE
import org.videolan.tools.NAGMEDAR_PLAYER_FULLSCREEN
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.NAGMEDAR_WHEEL_SEEKS
import org.videolan.tools.Settings

/**
 * Appearance and shell preferences of the new interface, stored in the app's shared
 * preferences next to the classic settings and observable from Compose.
 */
@Stable
class NagmedarPreferences(private val prefs: SharedPreferences) : PaneWidths {

    var themeMode by mutableStateOf(prefs.enumValue(KEY_THEME, ThemeMode.System))
        private set
    var accent by mutableStateOf(prefs.enumValue(KEY_ACCENT, Accent.Blue))
        private set
    var density by mutableStateOf(prefs.enumValue(KEY_DENSITY, DensityPreference.Auto))
        private set
    var sidebarCollapsed by mutableStateOf(prefs.getBoolean(KEY_SIDEBAR_COLLAPSED, false))
        private set

    /** S-408: the mouse wheel seeks in the video player, Ctrl+wheel sets the volume (else the other way round). */
    var wheelSeeks by mutableStateOf(prefs.getBoolean(NAGMEDAR_WHEEL_SEEKS, false))
        private set

    /** S-408: what the middle button does in the video player. */
    var middleClick by mutableStateOf(prefs.getString(NAGMEDAR_MIDDLE_CLICK, null) ?: NAGMEDAR_MIDDLE_PLAY_PAUSE)
        private set

    /** S-409: the video player opened from a desktop window asks to fill the screen. */
    var playerFullscreen by mutableStateOf(prefs.getBoolean(NAGMEDAR_PLAYER_FULLSCREEN, true))
        private set

    fun updatePlayerFullscreen(value: Boolean) {
        playerFullscreen = value
        prefs.edit().putBoolean(NAGMEDAR_PLAYER_FULLSCREEN, value).apply()
    }

    fun updateWheelSeeks(value: Boolean) {
        wheelSeeks = value
        prefs.edit().putBoolean(NAGMEDAR_WHEEL_SEEKS, value).apply()
    }

    fun updateMiddleClick(value: String) {
        middleClick = value
        prefs.edit().putString(NAGMEDAR_MIDDLE_CLICK, value).apply()
    }

    /** S-404: how long the mouse rests before a tooltip shows. */
    var tooltipDelay by mutableStateOf(prefs.enumValue(KEY_TOOLTIP_DELAY, TooltipDelay.Normal))
        private set

    /** S-309: the first-run flow was completed or skipped. */
    var firstRunDone by mutableStateOf(prefs.getBoolean(NAGMEDAR_FIRST_RUN_DONE, false))
        private set

    /** S-309: the launcher icon opens this interface (true) or the classic one. */
    var launchNewUi by mutableStateOf(prefs.getBoolean(NAGMEDAR_LAUNCH_NEW_UI, true))
        private set

    /** S-305: what was run from the command palette, the latest first. */
    var recentCommands by mutableStateOf(prefs.getString(KEY_PALETTE_RECENT, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty())
        private set

    /** S-402: the user's keys over the shipped shortcuts; the shortcut table follows them. */
    var shortcutOverrides by mutableStateOf(ShortcutOverrides.parse(prefs.getString(KEY_SHORTCUTS, null)))
        private set

    init {
        ShortcutRegistry.overrides = shortcutOverrides
    }

    /** S-309: tree URIs of the folders chosen with the system picker. */
    var safFolders by mutableStateOf(prefs.getStringSet(NAGMEDAR_SAF_FOLDERS, null)?.toSet().orEmpty())
        private set

    /** A preference changed in another window (S-307): this one follows. Held here: preferences keep listeners weakly. */
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> reload(key) }

    fun follow() = prefs.registerOnSharedPreferenceChangeListener(changes)

    fun stopFollowing() = prefs.unregisterOnSharedPreferenceChangeListener(changes)

    /** Reads [key] again; null (the preferences were cleared) reads them all. */
    internal fun reload(key: String?) {
        if (key == null || key == KEY_THEME) themeMode = prefs.enumValue(KEY_THEME, ThemeMode.System)
        if (key == null || key == KEY_ACCENT) accent = prefs.enumValue(KEY_ACCENT, Accent.Blue)
        if (key == null || key == KEY_DENSITY) density = prefs.enumValue(KEY_DENSITY, DensityPreference.Auto)
        if (key == null || key == KEY_SIDEBAR_COLLAPSED) sidebarCollapsed = prefs.getBoolean(KEY_SIDEBAR_COLLAPSED, false)
        if (key == null || key == NAGMEDAR_WHEEL_SEEKS) wheelSeeks = prefs.getBoolean(NAGMEDAR_WHEEL_SEEKS, false)
        if (key == null || key == NAGMEDAR_MIDDLE_CLICK) middleClick = prefs.getString(NAGMEDAR_MIDDLE_CLICK, null) ?: NAGMEDAR_MIDDLE_PLAY_PAUSE
        if (key == null || key == NAGMEDAR_PLAYER_FULLSCREEN) playerFullscreen = prefs.getBoolean(NAGMEDAR_PLAYER_FULLSCREEN, true)
        if (key == null || key == KEY_TOOLTIP_DELAY) tooltipDelay = prefs.enumValue(KEY_TOOLTIP_DELAY, TooltipDelay.Normal)
        if (key == null || key == NAGMEDAR_FIRST_RUN_DONE) firstRunDone = prefs.getBoolean(NAGMEDAR_FIRST_RUN_DONE, false)
        if (key == null || key == NAGMEDAR_LAUNCH_NEW_UI) launchNewUi = prefs.getBoolean(NAGMEDAR_LAUNCH_NEW_UI, true)
        if (key == null || key == KEY_PALETTE_RECENT) {
            recentCommands = prefs.getString(KEY_PALETTE_RECENT, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        }
        if (key == null || key == KEY_SHORTCUTS) {
            shortcutOverrides = ShortcutOverrides.parse(prefs.getString(KEY_SHORTCUTS, null))
            ShortcutRegistry.overrides = shortcutOverrides
        }
        if (key == null || key == NAGMEDAR_SAF_FOLDERS) safFolders = prefs.getStringSet(NAGMEDAR_SAF_FOLDERS, null)?.toSet().orEmpty()
    }

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        prefs.edit().putString(KEY_THEME, value.name).apply()
    }

    fun updateAccent(value: Accent) {
        accent = value
        prefs.edit().putString(KEY_ACCENT, value.name).apply()
    }

    fun updateDensity(value: DensityPreference) {
        density = value
        prefs.edit().putString(KEY_DENSITY, value.name).apply()
    }

    fun updateTooltipDelay(value: TooltipDelay) {
        tooltipDelay = value
        prefs.edit().putString(KEY_TOOLTIP_DELAY, value.name).apply()
    }

    fun updateSidebarCollapsed(value: Boolean) {
        sidebarCollapsed = value
        prefs.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, value).apply()
    }

    fun updateFirstRunDone(value: Boolean) {
        firstRunDone = value
        prefs.edit().putBoolean(NAGMEDAR_FIRST_RUN_DONE, value).apply()
    }

    fun updateLaunchNewUi(value: Boolean) {
        launchNewUi = value
        prefs.edit().putBoolean(NAGMEDAR_LAUNCH_NEW_UI, value).apply()
    }

    fun addSafFolder(uri: String) {
        safFolders = safFolders + uri
        prefs.edit().putStringSet(NAGMEDAR_SAF_FOLDERS, HashSet(safFolders)).apply()
    }

    fun removeSafFolder(uri: String) {
        safFolders = safFolders - uri
        prefs.edit().putStringSet(NAGMEDAR_SAF_FOLDERS, HashSet(safFolders)).apply()
    }

    fun updateShortcutOverrides(value: Map<ShortcutId, KeyChord?>) {
        shortcutOverrides = value
        ShortcutRegistry.overrides = value
        prefs.edit().putString(KEY_SHORTCUTS, ShortcutOverrides.format(value)).apply()
    }

    fun rememberCommand(id: String) {
        recentCommands = (listOf(id) + recentCommands.filter { it != id }).take(RECENT_COMMANDS)
        prefs.edit().putString(KEY_PALETTE_RECENT, recentCommands.joinToString("\n")).apply()
    }

    /** S-303: widths the panes were dragged to, in dp. */
    override fun paneWidth(key: String): Float? = prefs.getFloat(KEY_PANE_PREFIX + key, 0f).takeIf { it > 0f }

    override fun savePaneWidth(key: String, widthDp: Float) {
        prefs.edit().putFloat(KEY_PANE_PREFIX + key, widthDp).apply()
    }

    companion object {
        const val KEY_THEME = "nagmedar_theme"
        const val KEY_ACCENT = "nagmedar_accent"
        const val KEY_DENSITY = "nagmedar_density"
        const val KEY_SIDEBAR_COLLAPSED = "nagmedar_sidebar_collapsed"
        const val KEY_TOOLTIP_DELAY = "nagmedar_tooltip_delay"
        const val KEY_PANE_PREFIX = "nagmedar_pane_width_"
        const val KEY_PALETTE_RECENT = "nagmedar_palette_recent"
        const val KEY_SHORTCUTS = "nagmedar_shortcuts"
        const val RECENT_COMMANDS = 8
    }
}

@Composable
fun rememberNagmedarPreferences(): NagmedarPreferences {
    val context = LocalContext.current
    val prefs = remember { NagmedarPreferences(Settings.getInstance(context.applicationContext)) }
    // what another window changes, this one follows (S-307)
    DisposableEffect(prefs) {
        prefs.follow()
        onDispose { prefs.stopFollowing() }
    }
    return prefs
}

private inline fun <reified E : Enum<E>> SharedPreferences.enumValue(key: String, default: E): E {
    val stored = getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == stored } ?: default
}
