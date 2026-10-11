package app.nagmedar.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.NagmedarPreferences
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.PREF_FIRST_RUN
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.NAGMEDAR_FIRST_RUN_DONE
import org.videolan.tools.NAGMEDAR_NETWORK_SHARES
import org.videolan.tools.NAGMEDAR_ONLINE_SOURCES
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.onboarding.ONBOARDING_DONE_KEY

/**
 * The first-run flow with the real permission requests, pickers and medialibrary start (S-309).
 * [onDone] shows the library.
 */
@Composable
fun FirstRunHost(prefs: NagmedarPreferences, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val access = rememberAccessController(prefs)
    // the step survives rotation and window resizes; the rest is cheap to ask again
    val savedStep = rememberSaveable { mutableIntStateOf(0) }
    val state = remember { FirstRunState(FirstRunStep.entries[savedStep.intValue]) }
    val drives = remember { storageSources(context) }
    LaunchedEffect(state) { snapshotFlow { state.step.ordinal }.collect { savedStep.intValue = it } }
    SideEffect {
        state.mediaAccess = access.mediaAccess
        state.allFiles = access.allFiles
        state.notifications = access.notifications
        state.accessDenied = access.denied
    }
    FirstRunFlow(
        state = state,
        drives = drives,
        folders = access.folders.map { it to folderName(it) },
        actions = FirstRunActions(
            requestMediaAccess = access::requestMedia,
            requestAllFiles = access::requestAllFiles,
            openAppSettings = access::openAppSettings,
            pickFolder = access::pickFolder,
            removeFolder = access::removeFolder,
            playTestSound = {
                scope.launch {
                    state.audioPlaying = true
                    state.audioRoute = runCatching { AudioCheck.play(context) }.getOrElse { AudioRoute(AudioCheck.deviceName(context, null), 0) }
                    state.audioPlaying = false
                }
            },
            requestNotifications = access::requestNotifications,
            sourcesChosen = { scanSources(context, state.scanPaths(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, drives)) },
            finish = {
                completeFirstRun(context, state, scan = state.scanPaths(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, drives).isNotEmpty())
                prefs.updateFirstRunDone(true)
                onDone()
            },
        ),
    )
}

/**
 * Remembers that the flow is done, for this interface and for the classic one (which then skips
 * its own onboarding), and starts the medialibrary when the Sources step did not: scanning only
 * when there is media access and something to [scan].
 */
fun completeFirstRun(context: Context, state: FirstRunState, scan: Boolean) {
    Settings.getInstance(context).edit()
        .putInt(PREF_FIRST_RUN, BuildConfig.VLC_VERSION_CODE)
        .putBoolean(ONBOARDING_DONE_KEY, true)
        .putBoolean(NAGMEDAR_FIRST_RUN_DONE, true)
        .putBoolean(NAGMEDAR_NETWORK_SHARES, state.networkShares)
        .putBoolean(NAGMEDAR_ONLINE_SOURCES, state.onlineSources)
        .apply()
    context.startMedialibrary(firstRun = true, upgrade = true, parse = scan && MediaAccess.hasMediaAccess(context))
}
