package app.nagmedar.ui.radio

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import app.nagmedar.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.videolan.vlc.BuildConfig

/**
 * S-1105: the app's one radio: the directory client, the stations kept, the player and the
 * controller the screen reads. It outlives the screen, so a search and its place in the list stay
 * while another screen shows.
 */
object RadioCenter {

    /**
     * The directory (DNS and HTTP) and the logos are asked only in the app; tests and previews are
     * offline: the directory answers [RadioFailure.Offline] and no logo is fetched. It is set
     * before the first screen asks for [controller].
     */
    @Volatile
    var active = false
        set(value) {
            field = value
            RadioLogos.active = value
        }

    @Volatile
    private var controller: RadioController? = null

    fun controller(context: Context): RadioController = controller ?: synchronized(this) {
        controller ?: build(context.applicationContext).also { controller = it }
    }

    /** The directory to ask: the real one in the app, one that is never reachable otherwise. */
    internal fun directory(active: Boolean, version: String?): RadioDirectory =
        if (active) OkHttpRadioBrowser.create(version) else OfflineRadioDirectory

    private fun build(context: Context): RadioController {
        RadioLogos.identify(BuildConfig.VLC_VERSION_NAME)
        val directory = directory(active, BuildConfig.VLC_VERSION_NAME)
        val library = DatabaseRadioLibrary(context)
        val player = RadioPlayer(directory, library, ServiceStationSink(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        return RadioController(directory, library, player, scope)
    }
}

/** The radio screen in the app: its controller, and the clipboard for a stream's address. */
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    searchRequested: Boolean = false,
    onSearchHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val controller = remember(context) { RadioCenter.controller(context) }
    RadioPage(
        controller = controller,
        modifier = modifier,
        searchRequested = searchRequested,
        onSearchHandled = onSearchHandled,
        onCopyAddress = { station ->
            clipboard.setText(AnnotatedString(station.streamUrl))
            Toast.makeText(context, R.string.nag_radio_copied, Toast.LENGTH_SHORT).show()
        },
    )
}

/** A directory that cannot be reached: what tests and previews get, so nothing touches the network. */
internal object OfflineRadioDirectory : RadioDirectory {
    override suspend fun search(query: StationQuery): List<Station> = throw RadioFailure.Offline()

    override suspend fun countries(): List<DirectoryCountry> = throw RadioFailure.Offline()

    override suspend fun languages(): List<DirectoryLanguage> = throw RadioFailure.Offline()

    override suspend fun tags(): List<DirectoryTag> = throw RadioFailure.Offline()

    override suspend fun click(uuid: String): StationClick = throw RadioFailure.Offline()

    override suspend fun vote(uuid: String): VoteResult = throw RadioFailure.Offline()
}
