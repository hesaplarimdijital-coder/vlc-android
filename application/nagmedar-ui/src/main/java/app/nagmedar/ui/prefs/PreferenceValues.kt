package app.nagmedar.ui.prefs

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The values of "All settings" over VLC's own SharedPreferences: the same keys and types the
 * classic pages wrote (a CheckBoxPreference's Boolean, a ListPreference's String, a SeekBar's Int),
 * so the playback code reads them unchanged. A key that was never written reads as its XML default;
 * nothing is written until the user changes it. A change from anywhere else (the player, a restore)
 * redraws what reads it ([attach]).
 */
@Stable
class PreferenceValues(val prefs: SharedPreferences) {

    /** Bumped on every change, so that a read in composition is redone. */
    private var version by mutableIntStateOf(0)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }

    fun attach() = prefs.registerOnSharedPreferenceChangeListener(listener)

    fun detach() = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    fun bool(key: String, fallback: Boolean): Boolean {
        version
        return runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
    }

    fun string(key: String, fallback: String?): String? {
        version
        return runCatching { prefs.getString(key, fallback) }.getOrDefault(fallback)
    }

    fun int(key: String, fallback: Int): Int {
        version
        return runCatching { prefs.getInt(key, fallback) }.getOrDefault(fallback)
    }

    fun set(key: String, fallback: Set<String>): Set<String> {
        version
        return runCatching { prefs.getStringSet(key, fallback) }.getOrNull() ?: fallback
    }

    fun long(key: String, fallback: Long): Long {
        version
        return runCatching { prefs.getLong(key, fallback) }.getOrDefault(fallback)
    }

    // the item's own type, from its XML default

    fun checked(item: PrefItem): Boolean = bool(item.key.orEmpty(), item.defaultValue?.toBooleanStrictOrNull() ?: false)

    fun text(item: PrefItem): String = string(item.key.orEmpty(), item.defaultValue).orEmpty()

    fun number(item: PrefItem): Int = int(item.key.orEmpty(), item.defaultValue?.toIntOrNull() ?: item.min)

    fun selection(item: PrefItem): Set<String> = set(item.key.orEmpty(), item.defaultSet)

    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    fun putSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()

    fun remove(vararg keys: String) = prefs.edit().apply { keys.forEach(::remove) }.apply()
}
