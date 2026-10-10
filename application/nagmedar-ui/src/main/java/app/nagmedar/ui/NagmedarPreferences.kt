package app.nagmedar.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.ThemeMode
import org.videolan.tools.Settings

/**
 * Appearance and shell preferences of the new interface, stored in the app's shared
 * preferences next to the classic settings and observable from Compose.
 */
@Stable
class NagmedarPreferences(private val prefs: SharedPreferences) {

    var themeMode by mutableStateOf(prefs.enumValue(KEY_THEME, ThemeMode.System))
        private set
    var accent by mutableStateOf(prefs.enumValue(KEY_ACCENT, Accent.Blue))
        private set
    var density by mutableStateOf(prefs.enumValue(KEY_DENSITY, DensityPreference.Auto))
        private set
    var sidebarCollapsed by mutableStateOf(prefs.getBoolean(KEY_SIDEBAR_COLLAPSED, false))
        private set

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

    fun updateSidebarCollapsed(value: Boolean) {
        sidebarCollapsed = value
        prefs.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, value).apply()
    }

    companion object {
        const val KEY_THEME = "nagmedar_theme"
        const val KEY_ACCENT = "nagmedar_accent"
        const val KEY_DENSITY = "nagmedar_density"
        const val KEY_SIDEBAR_COLLAPSED = "nagmedar_sidebar_collapsed"
    }
}

@Composable
fun rememberNagmedarPreferences(): NagmedarPreferences {
    val context = LocalContext.current
    return remember { NagmedarPreferences(Settings.getInstance(context.applicationContext)) }
}

private inline fun <reified E : Enum<E>> SharedPreferences.enumValue(key: String, default: E): E {
    val stored = getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == stored } ?: default
}
