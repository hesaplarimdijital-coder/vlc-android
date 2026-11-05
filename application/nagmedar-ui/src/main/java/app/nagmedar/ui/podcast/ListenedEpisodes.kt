package app.nagmedar.ui.podcast

import android.content.Context
import android.content.SharedPreferences

/**
 * 1.0.3: the episodes in the order they were last listened to, the latest first, at most [MAX] of them: Android Auto's
 * "Devam eden bölümler" lists those left in the middle in this order. The medialibrary's history cannot give it: VLC
 * puts a long audio (an episode, to it a "podcast") there only the first time it is played, so an episode taken up
 * again never comes back to the top. Pure: the order of ids, kept as text.
 */
object ListenedOrder {
    const val MAX = 200

    /** [order] with [episodeId] first, each id once, cut at [MAX]. */
    fun heard(order: List<Long>, episodeId: Long): List<Long> = (listOf(episodeId) + order.filter { it != episodeId }).take(MAX)

    fun write(order: List<Long>): String = order.joinToString(",")

    /** The ids of [text]; what is no id is left out. */
    fun read(text: String?): List<Long> = text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull()?.takeIf { id -> id > 0 } }.distinct()
}

/**
 * 1.0.3: [ListenedOrder] kept in the app's own preferences (not backed up: the episodes' ids are this device's). Written
 * when an episode starts to play ([PodcastPlaybackObserver]), read by Android Auto. Thread-safe; cheap.
 */
class ListenedEpisodes(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The episodes, the one listened to last first. */
    fun order(): List<Long> = ListenedOrder.read(prefs.getString(KEY, null))

    /** [episodeId] is being listened to: it goes first. Whether the order changed (it was not first already). */
    fun heard(episodeId: Long): Boolean = synchronized(LOCK) {
        val order = order()
        if (order.firstOrNull() == episodeId) return false
        prefs.edit().putString(KEY, ListenedOrder.write(ListenedOrder.heard(order, episodeId))).apply()
        true
    }

    private companion object {
        const val PREFS = "nagmedar_podcast_listened"
        const val KEY = "order"
        val LOCK = Any()
    }
}
