package app.nagmedar.ui.player.subtitles.providers

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which providers are turned on and in what order (Settings › Subtitles). */
@Immutable
data class ProviderSettings(
    /** The ids in the order wanted; a provider not in it comes after, in its own order. */
    val order: List<String> = emptyList(),
    /** The ids turned off; a provider not in it is on, unless it is off by default. */
    val off: Set<String> = emptySet(),
    /** The ids turned on that are off by default. */
    val on: Set<String> = emptySet(),
)

interface ProviderSettingsStore {
    fun load(): ProviderSettings

    fun save(settings: ProviderSettings)
}

/** The settings in the app's preferences, three lists of ids: "opensubtitles,local-folder;opensubtitles;local-folder" (order; off; on). */
class PrefsProviderSettingsStore(private val prefs: SharedPreferences) : ProviderSettingsStore {
    override fun load(): ProviderSettings {
        val parts = prefs.getString(KEY, null)?.split(';').orEmpty()
        fun ids(index: Int) = parts.getOrNull(index).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return ProviderSettings(ids(0), ids(1).toSet(), ids(2).toSet())
    }

    override fun save(settings: ProviderSettings) {
        val text = listOf(settings.order, settings.off, settings.on).joinToString(";") { it.joinToString(",") }
        prefs.edit().putString(KEY, text).apply()
    }

    companion object {
        const val KEY = "nagmedar_subtitle_providers"
    }
}

/**
 * The subtitle providers the app has (S-707) and the user's choice among them: which are turned
 * on, in what order. The order is the order of preference: where two providers have the same
 * file, the earlier one's is kept. A provider that needs something of the user's (a folder) is
 * off until the user turns it on.
 */
@Stable
class ProviderRegistry(
    private val all: List<SubtitleProvider>,
    private val store: ProviderSettingsStore,
    /** Ids of the providers that start turned off. */
    private val offByDefault: Set<String> = emptySet(),
) {
    var settings by mutableStateOf(store.load())
        private set

    /** All providers, in the order wanted. */
    val ordered: List<SubtitleProvider>
        get() {
            val rank = settings.order.withIndex().associate { (index, id) -> id to index }
            return all.withIndex().sortedBy { (index, provider) -> rank[provider.id] ?: (settings.order.size + index) }.map { it.value }
        }

    fun isEnabled(id: String): Boolean = if (id in offByDefault) id in settings.on else id !in settings.off

    /** The providers turned on, in the order wanted. */
    fun enabled(): List<SubtitleProvider> = ordered.filter { isEnabled(it.id) }

    fun byId(id: String): SubtitleProvider? = all.firstOrNull { it.id == id }

    fun setEnabled(id: String, on: Boolean) {
        if (byId(id) == null) return
        change(
            if (id in offByDefault) settings.copy(on = if (on) settings.on + id else settings.on - id) else settings.copy(off = if (on) settings.off - id else settings.off + id),
        )
    }

    /** [id] one place earlier ([by] negative) or later. */
    fun move(id: String, by: Int) {
        val ids = ordered.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        if (from < 0) return
        val to = (from + by).coerceIn(0, ids.lastIndex)
        if (to == from) return
        ids.add(to, ids.removeAt(from))
        change(settings.copy(order = ids))
    }

    private fun change(next: ProviderSettings) {
        settings = next
        store.save(next)
    }
}
