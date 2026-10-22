package org.videolan.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Nağmedar: the app's short notices ("3 tracks added to the queue", "Copied", a "Loading…" that
 * lasts while something works), posted from anywhere (the new interface, the playback service, the
 * library) and shown by the window in front: the notice host of Nağmedar's window or of the video
 * player. With no host up, a plain notice is the system's toast and a progress shows nowhere: no
 * window of the app is there to be loading in.
 */
object Notices {

    /** One notice. [progress] stays until it is dismissed; the others leave by themselves, [long] ones later. */
    class Notice internal constructor(
        val id: Long,
        val text: String,
        val long: Boolean,
        val error: Boolean,
        val progress: Boolean,
        val actionLabel: String?,
        val action: (() -> Unit)?,
    )

    private val ids = AtomicLong(1)
    private val main = Handler(Looper.getMainLooper())
    private val _shown = MutableStateFlow<List<Notice>>(emptyList())

    /** The notices on screen, oldest first; at most [MAX_SHOWN]. */
    val shown: StateFlow<List<Notice>> = _shown.asStateFlow()

    /** The hosts in front, the last one started showing the notices. */
    private val hosts = ArrayList<Any>()
    private val _host = MutableStateFlow<Any?>(null)

    /** The host that shows the notices now; null when none is up. */
    val host: StateFlow<Any?> = _host.asStateFlow()

    /** [token]'s window came to the front: it shows the notices. */
    fun hostStarted(token: Any) = synchronized(hosts) {
        hosts.remove(token)
        hosts += token
        _host.value = token
    }

    /** [token]'s window went: the one before it shows them, if one is still up. */
    fun hostStopped(token: Any) = synchronized(hosts) {
        hosts.remove(token)
        _host.value = hosts.lastOrNull()
    }

    private val hasHost: Boolean get() = synchronized(hosts) { hosts.isNotEmpty() }

    /** Shows [text]; an [action] adds a button (the host's only, not the toast's). Any thread. */
    fun show(
        context: Context,
        text: CharSequence,
        long: Boolean = false,
        error: Boolean = false,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ) {
        if (!hasHost) {
            val app = context.applicationContext
            main.post { Toast.makeText(app, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
            return
        }
        add(Notice(ids.getAndIncrement(), text.toString(), long, error, progress = false, actionLabel, action))
    }

    fun show(context: Context, @StringRes text: Int, long: Boolean = false, error: Boolean = false) =
        show(context, context.getString(text), long, error)

    /** A notice that says something is being done, until [dismiss] with the id this gives; nothing without a host. */
    fun progress(text: String): Long {
        val id = ids.getAndIncrement()
        if (hasHost) add(Notice(id, text, long = true, error = false, progress = true, actionLabel = null, action = null))
        return id
    }

    /** The notice [id] leaves (shown or not). Any thread. */
    fun dismiss(id: Long) {
        _shown.update { list -> list.filterNot { it.id == id } }
    }

    private fun add(notice: Notice) {
        _shown.update { list ->
            // the same words again replace the notice that says them
            val kept = list.filterNot { !it.progress && it.text == notice.text }
            (kept + notice).takeLast(MAX_SHOWN)
        }
    }

    const val MAX_SHOWN = 3
}
