package app.nagmedar.ui.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nagmedar.network.LocalNetwork
import app.nagmedar.network.LocalNetworkState
import app.nagmedar.ui.R
import app.nagmedar.ui.lab.findActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.tools.NAGMEDAR_LOCAL_NETWORK_ASKED
import org.videolan.tools.Notices
import org.videolan.tools.Settings

/**
 * Android 17: the local network permission in the app ([LocalNetwork] says what it is). Its [state], read again when a
 * window of the app resumes and after the system's question; [ask] puts the question from the window in front
 * ([LocalNetworkHost]), or opens the app's page in the system settings once Android no longer asks. The pages that
 * reach the local network (Sources › Network, the device picker, remote access) say why in the app's words and ask
 * when it is needed; a media on the local network opened without it is said in a notice ([install]).
 */
object LocalNetworkAccess {
    private val _state = MutableStateFlow(LocalNetworkState.Granted)

    /** Granted until read: Android 16, and an app that targets 36, never ask. */
    val state: StateFlow<LocalNetworkState> = _state.asStateFlow()
    private var read = false

    /** When a page last asked (the clock's milliseconds); a window puts the question once. */
    private val _requests = MutableStateFlow(0L)
    internal val requests: StateFlow<Long> = _requests.asStateFlow()
    private var taken = 0L

    /** The state read when nothing read it yet (a page composed before its window resumed). Main thread. */
    fun prime(context: Context) {
        if (!read) refresh(context, rationale = asked(context))
    }

    /** The state read again; [rationale] is Android's word that it would still ask (a window knows it). Main thread. */
    fun refresh(context: Context, rationale: Boolean) {
        read = true
        _state.value = LocalNetwork.state(LocalNetwork.required(context), LocalNetwork.granted(context), asked(context), rationale)
    }

    /** The permission asked for: the system's question in the window in front, or the system settings once Android no longer asks. */
    fun ask(context: Context) {
        when (state.value) {
            LocalNetworkState.Granted -> Unit
            LocalNetworkState.Blocked -> openSettings(context)
            LocalNetworkState.Ask, LocalNetworkState.Denied -> _requests.value = SystemClock.elapsedRealtime().coerceAtLeast(_requests.value + 1)
        }
    }

    /** Whether a window puts the question asked at [at]: the first one to see it, while it is recent. Main thread. */
    internal fun take(at: Long, now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (at <= taken || now - at > FRESH_MS) return false
        taken = at
        return true
    }

    /** The system answered (given or refused; [rationale]: Android would ask again). */
    internal fun answered(context: Context, rationale: Boolean) {
        Settings.getInstance(context).edit().putBoolean(NAGMEDAR_LOCAL_NETWORK_ASKED, true).apply()
        refresh(context, rationale)
    }

    /** The app's page in the system settings, where "Nearby devices" is allowed. */
    fun openSettings(context: Context) {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun asked(context: Context): Boolean = Settings.getInstance(context).getBoolean(NAGMEDAR_LOCAL_NETWORK_ASKED, false)

    /**
     * A media on the local network opened without the permission (the playback service tells it): said once a host, in
     * a notice with the way to give it (the system settings when Android no longer asks), or a toast with no window up.
     */
    fun install(context: Context) {
        val app = context.applicationContext
        LocalNetwork.onBlocked = onBlocked@{ host ->
            synchronized(told) { if (!told.add(host)) return@onBlocked }
            val label = app.getString(if (state.value == LocalNetworkState.Blocked) R.string.nag_lan_open_settings else R.string.nag_lan_allow)
            Notices.show(app, app.getString(R.string.nag_lan_media_blocked, host), long = true, actionLabel = label) { ask(app) }
        }
    }

    /** The hosts already said in this process. */
    private val told = HashSet<String>()

    /** How long a page's request waits for a window to put the question. */
    private const val FRESH_MS = 5_000L
}

/** The local network permission's state for a page, read once if nothing read it yet; it follows the system's answers. */
@Composable
fun rememberLocalNetworkState(): LocalNetworkState {
    val context = LocalContext.current
    // read before the first frame; the window's host reads it again when it resumes
    remember(context) {
        LocalNetworkAccess.prime(context)
        true
    }
    val state by LocalNetworkAccess.state.collectAsState()
    return state
}

/** What the pages say of the local network permission: the title, the reason in the app's words, the way to give it. */
internal data class LocalNetworkWords(val title: Int, val description: Int, val action: Int)

/** The words for [state] (null when nothing needs saying). */
internal fun localNetworkWords(state: LocalNetworkState): LocalNetworkWords? = when (state) {
    LocalNetworkState.Granted -> null
    LocalNetworkState.Ask -> LocalNetworkWords(R.string.nag_lan_ask_title, R.string.nag_lan_ask_desc, R.string.nag_lan_allow)
    LocalNetworkState.Denied -> LocalNetworkWords(R.string.nag_lan_denied_title, R.string.nag_lan_denied_desc, R.string.nag_lan_allow)
    LocalNetworkState.Blocked -> LocalNetworkWords(R.string.nag_lan_denied_title, R.string.nag_lan_blocked_desc, R.string.nag_lan_open_settings)
}

/**
 * Android 17: a window's part in the local network permission: the state read again when it resumes (the user may
 * have changed it in the system settings), the system's question put when a page asks. In Nağmedar's window, beside
 * the libVLC dialogs; a window in the background leaves the question to the one in front.
 */
@Composable
fun LocalNetworkHost() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LocalNetworkAccess.answered(context, rationale(activity))
    }
    LifecycleResumeEffect(activity) {
        LocalNetworkAccess.refresh(context, rationale(activity))
        onPauseOrDispose {}
    }
    val request by LocalNetworkAccess.requests.collectAsState()
    LaunchedEffect(request) {
        if (request > 0 && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && LocalNetworkAccess.take(request)) {
            runCatching { launcher.launch(LocalNetwork.PERMISSION) }
        }
    }
}

private fun rationale(activity: Activity?): Boolean =
    activity?.shouldShowRequestPermissionRationale(LocalNetwork.PERMISSION) == true
