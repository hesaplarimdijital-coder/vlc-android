package app.nagmedar.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.library.SafLibrary

/**
 * Media permission, all-files access, notifications and picked folders, for the first-run flow
 * and the library's permission state (S-309). The values refresh whenever the window resumes,
 * so a change made in the system settings shows up at once.
 */
@Stable
class AccessController internal constructor(private val context: Context, private val prefs: NagmedarPreferences) {
    var mediaAccess by mutableStateOf(MediaAccess.hasMediaAccess(context))
        private set
    var allFiles by mutableStateOf(MediaAccess.hasAllFiles(context))
        private set
    var notifications by mutableStateOf(MediaAccess.hasNotifications(context))
        private set

    /** The last media permission request was refused. */
    var denied by mutableStateOf(false)
        internal set

    internal var launchers: Launchers? = null

    internal class Launchers(
        val media: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
        val settings: ManagedActivityResultLauncher<Intent, ActivityResult>,
        val notifications: ManagedActivityResultLauncher<String, Boolean>,
        val folder: ManagedActivityResultLauncher<Uri?, Uri?>,
    )

    val folders: Set<String> get() = prefs.safFolders

    fun refresh() {
        mediaAccess = MediaAccess.hasMediaAccess(context)
        allFiles = MediaAccess.hasAllFiles(context)
        notifications = MediaAccess.hasNotifications(context)
        if (mediaAccess) denied = false
    }

    fun requestMedia() {
        launchers?.media?.launch(MediaAccess.mediaPermissions)
    }

    fun requestAllFiles() {
        openSettings(MediaAccess.allFilesSettings(context))
    }

    fun openAppSettings() {
        openSettings(MediaAccess.appSettings(context))
    }

    fun requestNotifications() {
        val permission = MediaAccess.notificationPermission
        if (permission == null) refresh() else launchers?.notifications?.launch(permission)
    }

    fun pickFolder() {
        launchers?.folder?.launch(null)
    }

    internal fun folderPicked(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        SafLibrary.forget(uri.toString())
        prefs.addSafFolder(uri.toString())
    }

    fun removeFolder(uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        SafLibrary.forget(uri)
        prefs.removeSafFolder(uri)
    }

    /** Some devices have no all-files page: the app's own settings page is the way there. */
    private fun openSettings(intent: Intent) {
        val launcher = launchers?.settings ?: return
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            runCatching { launcher.launch(MediaAccess.appSettings(context)) }
        }
    }
}

/** "Movies" for content://…/tree/primary%3AMovies, the drive's own name for its root. */
fun folderName(uri: String): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrNull() ?: return uri
    val path = id.substringAfter(':', id)
    return path.trimEnd('/').substringAfterLast('/').ifEmpty { id.substringBefore(':') }
}

/** [onMediaGranted] runs when a request made here is granted (the library starts scanning then). */
@Composable
fun rememberAccessController(prefs: NagmedarPreferences, onMediaGranted: () -> Unit = {}): AccessController {
    val context = LocalContext.current
    val controller = remember(prefs) { AccessController(context.applicationContext, prefs) }
    val granted by rememberUpdatedState(onMediaGranted)
    val media = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        controller.refresh()
        if (controller.mediaAccess) granted() else controller.denied = true
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val before = controller.mediaAccess
        controller.refresh()
        if (!before && controller.mediaAccess) granted()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { controller.refresh() }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) controller.folderPicked(uri)
    }
    SideEffect { controller.launchers = AccessController.Launchers(media, settings, notifications, folder) }
    LifecycleResumeEffect(controller) {
        controller.refresh()
        onPauseOrDispose {}
    }
    return controller
}
