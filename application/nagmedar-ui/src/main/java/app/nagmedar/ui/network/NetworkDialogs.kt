package app.nagmedar.ui.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.tools.AppScope
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.LibVlcDialogHandler
import org.videolan.vlc.util.LibVlcDialogHook
import java.util.IdentityHashMap

/**
 * S-1301: libVLC's requests as Nağmedar takes them ([LibVlcDialogHook], before VLC's windows):
 * each one made a [LibVlcPrompt] and sent where [router] says. Installed when the app starts, so
 * that the library's scan of a saved server and a file played after a restart sign in too.
 */
object NetworkDialogs : LibVlcDialogHandler {
    private val main = Handler(Looper.getMainLooper())

    /** The requests taken, by libVLC's dialog; main thread. */
    private val taken = IdentityHashMap<Dialog, LibVlcPrompt>()

    /** Requests given back to VLC's windows: not taken again when they come through them; main thread. */
    private val handedBack = java.util.Collections.newSetFromMap(IdentityHashMap<Dialog, Boolean>())

    @Volatile
    private var store: ServerStore? = null

    val router = DialogRouter(savedSignIn = ::savedSignIn, now = SystemClock::elapsedRealtime, handBack = ::handBack)

    /**
     * The requests the app's window shows, kept here for as long as the process lives: a window
     * recreated (a new language) shows them again; with none in front for [HANDOVER_MS], they go
     * to VLC's windows, so libVLC does not wait for nobody.
     */
    val queue = PromptQueue()
    private var windows = 0
    private val handOver = Runnable {
        if (windows == 0) queue.shown.toList().forEach(::handBack)
    }

    /** A window of the new interface is in front: it shows the requests. Main thread. */
    fun windowStarted() {
        windows++
        main.removeCallbacks(handOver)
        router.host = queue
    }

    /** It is no longer in front; with none left, the requests go to VLC's windows a little later. Main thread. */
    fun windowStopped() {
        windows = (windows - 1).coerceAtLeast(0)
        if (windows > 0) return
        router.host = null
        main.postDelayed(handOver, HANDOVER_MS)
    }

    private const val HANDOVER_MS = 10_000L

    /** Takes libVLC's requests from now on, and follows the saved servers' hosts. */
    @Synchronized
    fun install(context: Context) {
        if (store != null) return
        val servers = DatabaseServerStore(context)
        store = servers
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
        if (handedBack.remove(dialog)) return false
        val prompt = promptOf(dialog) ?: return false
        // known before it is routed: an answer at once lets go of it
        taken[dialog] = prompt
        if (router.route(prompt)) return true
        taken.remove(dialog)
        return false
    }

    /** [prompt] given to VLC's windows, as if Nağmedar had not seen it. Main thread. */
    private fun handBack(prompt: LibVlcPrompt) {
        queue.shown.remove(prompt)
        val dialog = taken.entries.firstOrNull { it.value === prompt }?.key ?: return
        taken.remove(dialog)
        handedBack += dialog
        when (dialog) {
            is Dialog.LoginDialog -> DialogDelegate.onDisplay(dialog)
            is Dialog.QuestionDialog -> DialogDelegate.onDisplay(dialog)
            is Dialog.ProgressDialog -> DialogDelegate.onDisplay(dialog)
            else -> handedBack -= dialog
        }
    }

    override fun canceled(dialog: Dialog): Boolean {
        handedBack -= dialog
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
