package app.nagmedar.ui.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.tools.AppScope
import org.videolan.vlc.util.LibVlcDialogHandler
import org.videolan.vlc.util.LibVlcDialogHook
import java.util.IdentityHashMap

/**
 * S-1301: libVLC's requests as Nağmedar takes them ([LibVlcDialogHook], before VLC's windows):
 * each one made a [LibVlcPrompt] and sent where [router] says. Installed when the app starts, so
 * that the library's scan of a saved server and a file played after a restart sign in too. What
 * nobody answers waits in [queue] for a window of the app; with none in front, a notification
 * says so (VLC's DialogActivity is no longer started from the background).
 */
object NetworkDialogs : LibVlcDialogHandler {
    private val main = Handler(Looper.getMainLooper())

    /** The requests taken, by libVLC's dialog; main thread. */
    private val taken = IdentityHashMap<Dialog, LibVlcPrompt>()

    @Volatile
    private var store: ServerStore? = null

    @Volatile
    private var notification: PromptNotification? = null

    val router = DialogRouter(savedSignIn = ::savedSignIn, now = SystemClock::elapsedRealtime)

    /**
     * The requests the app's window shows, kept here for as long as the process lives: a window
     * recreated (a new language) shows them again; one taken while no window is in front, or left
     * unanswered [NOTIFY_AFTER_MS] after the last window went, is said in a notification.
     */
    val queue = PromptQueue()
    private var windows = 0
    private val notify = Runnable { if (windows == 0) notifyWaiting() }

    /** A window of the new interface is in front: it shows the requests. Main thread. */
    fun windowStarted() {
        windows++
        main.removeCallbacks(notify)
        notification?.cancel()
    }

    /** It is no longer in front; with none left, what waits is said in a notification a little later. Main thread. */
    fun windowStopped() {
        windows = (windows - 1).coerceAtLeast(0)
        if (windows > 0) return
        main.postDelayed(notify, NOTIFY_AFTER_MS)
    }

    private const val NOTIFY_AFTER_MS = 10_000L

    /** The questions and sign-ins waiting (a progress needs no answer), in a notification; none left takes it away. */
    private fun notifyWaiting() {
        val waiting = queue.shown.filter { it.kind != PromptKind.Progress && !it.settled }
        val notification = notification ?: return
        if (waiting.isEmpty()) notification.cancel() else notification.post(waiting.first(), waiting.size)
    }

    /** Takes libVLC's requests from now on, and follows the saved servers' hosts. */
    @Synchronized
    fun install(context: Context) {
        if (store != null) return
        val servers = DatabaseServerStore(context)
        store = servers
        notification = PromptNotification(context.applicationContext)
        // the window shows what nobody else answers; with no window in front, the notification says it waits
        router.host = object : PromptHost {
            override fun show(prompt: LibVlcPrompt): Boolean {
                queue.show(prompt)
                if (windows == 0) notifyWaiting()
                return true
            }

            override fun gone(prompt: LibVlcPrompt): Boolean {
                val shown = queue.gone(prompt)
                if (shown && windows == 0) notifyWaiting()
                return shown
            }
        }
        LibVlcDialogHook.handler = this
        AppScope.launch {
            runCatching {
                servers.servers.collect { rows ->
                    router.savedHosts = rows.filter { it.signedIn }.mapNotNull { ServerAddress.parse(it.url)?.host }.toSet()
                }
            }
        }
    }

    override fun display(dialog: Dialog): Boolean {
        val prompt = promptOf(dialog) ?: return false
        // known before it is routed: an answer at once lets go of it
        taken[dialog] = prompt
        if (router.route(prompt)) return true
        taken.remove(dialog)
        return false
    }

    override fun canceled(dialog: Dialog): Boolean {
        val prompt = taken.remove(dialog) ?: return false
        router.canceled(prompt)
        return true
    }

    override fun progress(dialog: Dialog.ProgressDialog): Boolean {
        val prompt = taken[dialog] ?: return false
        prompt.position = dialog.position
        prompt.text = dialog.text.orEmpty()
        return true
    }

    private fun savedSignIn(words: String, reply: (Credentials?) -> Unit) {
        val servers = store ?: return reply(null)
        AppScope.launch(Dispatchers.IO) {
            val credentials = runCatching { servers.signInNamed(words) }.getOrNull()
            main.post { reply(credentials) }
        }
    }

    private fun promptOf(dialog: Dialog): LibVlcPrompt? {
        val reply = object : PromptReply {
            override fun login(user: String, password: String, store: Boolean) = onMain {
                taken.remove(dialog)
                (dialog as? Dialog.LoginDialog)?.postLogin(user, password, store)
            }

            override fun action(action: Int) = onMain {
                taken.remove(dialog)
                (dialog as? Dialog.QuestionDialog)?.postAction(action)
            }

            override fun dismiss() = onMain {
                taken.remove(dialog)
                dialog.dismiss()
            }
        }
        val title = dialog.title.orEmpty()
        val text = dialog.text.orEmpty()
        return when (dialog) {
            is Dialog.LoginDialog -> LibVlcPrompt(PromptKind.Login, title, text, defaultUser = dialog.defaultUsername, asksStore = dialog.asksStore(), reply = reply)
            is Dialog.QuestionDialog -> LibVlcPrompt(
                PromptKind.Question, title, text,
                cancelText = dialog.cancelText, action1 = dialog.action1Text, action2 = dialog.action2Text,
                warning = dialog.questionType != Dialog.QuestionDialog.TYPE_NORMAL, reply = reply,
            )
            is Dialog.ProgressDialog -> LibVlcPrompt(
                PromptKind.Progress, title, text,
                cancelText = dialog.cancelText, indeterminate = dialog.isIndeterminate, position = dialog.position, reply = reply,
            )
            else -> null
        }
    }

    /** libVLC's dialogs are answered on the main thread. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
