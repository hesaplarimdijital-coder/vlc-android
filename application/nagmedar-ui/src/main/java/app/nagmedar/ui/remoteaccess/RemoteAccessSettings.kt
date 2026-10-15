package app.nagmedar.ui.remoteaccess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.videolan.resources.REMOTE_ACCESS_ONBOARDING
import org.videolan.resources.util.restartRemoteAccess
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.KEY_REMOTE_ACCESS_LAST_STATE_STOPPED
import org.videolan.tools.KEY_REMOTE_ACCESS_ML_CONTENT
import org.videolan.tools.REMOTE_ACCESS_FILE_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_HISTORY_CONTENT
import org.videolan.tools.REMOTE_ACCESS_LOGS
import org.videolan.tools.REMOTE_ACCESS_NETWORK_BROWSER_CONTENT
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL

/**
 * What the remote access server is asked to do (S-1304). The app's [AppRemoteAccessControl] reaches
 * the service the classic settings reach; the tests record the calls.
 */
interface RemoteAccessControl {
    /** Start the service (the server in it, unless it was stopped on purpose). */
    fun start()

    /** Stop the server and the service. */
    fun stop()

    /** Stop the server and start it again, for a change that only a restart applies. */
    fun restart()

    /** The page with the server's status, its addresses with their QR codes, and the connections. */
    fun showAddresses()

    /** The pages that explain what it is, the encryption, the one-time code and the content. */
    fun showHowItWorks()
}

/** The parts of the media library a browser may see; [value] is what the classic setting stores. */
enum class LibraryPart(val value: String) {
    Video("0"),
    Audio("1"),
    Playlists("2"),
    Search("3"),
    ;

    companion object {
        /** Every part: the classic setting's default (`remote_access_content_values`). */
        val All: Set<LibraryPart> = entries.toSet()

        /** The parts [values] name; a value no part has is passed over. */
        fun parse(values: Set<String>): Set<LibraryPart> = entries.filter { it.value in values }.toSet()
    }
}

/**
 * S-1304: the remote access settings (Settings › Remote access) over the preferences the classic
 * screen and the server read: the switch, which parts of the library, the file and network browsers,
 * the history, playback control and the log files. The defaults are the classic screen's: only the
 * library and playback control are on. Turning it on starts the service, turning it off stops it;
 * the network browser is only taken up by a restart, as in the classic screen. The server asks the
 * user for a one-time code on every new browser whatever is set here, and is always HTTPS.
 *
 * The first time it is turned on, the pages that explain it are shown ([RemoteAccessControl.showHowItWorks]).
 * The notification's "Disable" turns the switch off from outside: [attach] follows it.
 */
@Stable
class RemoteAccessSettings(private val prefs: SharedPreferences, private val control: RemoteAccessControl) {

    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false))
        private set

    var library by mutableStateOf(readLibrary())
        private set

    var files by mutableStateOf(prefs.getBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, false))
        private set

    var network by mutableStateOf(prefs.getBoolean(REMOTE_ACCESS_NETWORK_BROWSER_CONTENT, false))
        private set

    var history by mutableStateOf(prefs.getBoolean(REMOTE_ACCESS_HISTORY_CONTENT, false))
        private set

    var playback by mutableStateOf(prefs.getBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, true))
        private set

    var logs by mutableStateOf(prefs.getBoolean(REMOTE_ACCESS_LOGS, false))
        private set

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ENABLE_REMOTE_ACCESS) enabled = prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false)
    }

    /** Follow the switch when something else turns it off (the notification). Pair with [detach]. */
    fun attach() {
        enabled = prefs.getBoolean(KEY_ENABLE_REMOTE_ACCESS, false)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun detach() = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    fun enable(on: Boolean) {
        if (on == enabled) return
        enabled = on
        val firstTime = on && !prefs.getBoolean(REMOTE_ACCESS_ONBOARDING, false)
        prefs.edit().apply {
            putBoolean(KEY_ENABLE_REMOTE_ACCESS, on)
            // asking for it is asking for it to run, also after the server was stopped on purpose
            if (on) putBoolean(KEY_REMOTE_ACCESS_LAST_STATE_STOPPED, false)
            if (firstTime) putBoolean(REMOTE_ACCESS_ONBOARDING, true)
        }.apply()
        if (on) control.start() else control.stop()
        if (firstTime) control.showHowItWorks()
    }

    fun showLibraryPart(part: LibraryPart, shown: Boolean) {
        library = if (shown) library + part else library - part
        prefs.edit().putStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, library.map { it.value }.toSet()).apply()
    }

    fun showFiles(on: Boolean) {
        files = on
        prefs.edit().putBoolean(REMOTE_ACCESS_FILE_BROWSER_CONTENT, on).apply()
    }

    fun showNetwork(on: Boolean) {
        network = on
        prefs.edit().putBoolean(REMOTE_ACCESS_NETWORK_BROWSER_CONTENT, on).apply()
        // the shares are found when the server starts
        control.restart()
    }

    fun showHistory(on: Boolean) {
        history = on
        prefs.edit().putBoolean(REMOTE_ACCESS_HISTORY_CONTENT, on).apply()
    }

    fun controlPlayback(on: Boolean) {
        playback = on
        prefs.edit().putBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, on).apply()
    }

    fun shareLogs(on: Boolean) {
        logs = on
        prefs.edit().putBoolean(REMOTE_ACCESS_LOGS, on).apply()
    }

    fun showAddresses() = control.showAddresses()

    fun showHowItWorks() = control.showHowItWorks()

    private fun readLibrary(): Set<LibraryPart> {
        val stored = prefs.getStringSet(KEY_REMOTE_ACCESS_ML_CONTENT, null) ?: return LibraryPart.All
        return LibraryPart.parse(stored)
    }
}

/** The app's [RemoteAccessControl]: the service and the pages of the classic interface. */
class AppRemoteAccessControl(private val context: Context) : RemoteAccessControl {
    override fun start() = context.startRemoteAccess()

    override fun stop() = context.stopRemoteAccess()

    override fun restart() = context.restartRemoteAccess()

    // the classic start-up's own route to the status page, as the classic settings take it
    override fun showAddresses() = open(Intent().setClassName(context, START_ACTIVITY).setAction(SHARE_ACTION))

    override fun showHowItWorks() = open(Intent(Intent.ACTION_VIEW).setComponent(ComponentName(context, REMOTE_ACCESS_ONBOARDING)))

    private fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private companion object {
        const val START_ACTIVITY = "org.videolan.vlc.StartActivity"
        const val SHARE_ACTION = "vlc.remoteaccess.share"
    }
}
