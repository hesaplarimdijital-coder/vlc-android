package app.nagmedar.ui.scrobble

import android.content.Context
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.BuildConfig
import kotlinx.coroutines.Dispatchers
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig as VlcBuildConfig

/**
 * S-1008: the app's scrobbling, built once: the settings and accounts, the queue, the two services,
 * the hub the playback service feeds and the flusher the background job runs. Nothing is touched
 * (not the database, not the keystore) until a part is used.
 */
class Scrobbling private constructor(context: Context) {
    val settings = ScrobbleSettings(Settings.getInstance(context), KeystoreSecretBox())
    val services: List<ScrobbleService> = listOf(
        LastFmService(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SECRET, userAgent()),
        ListenBrainzService(VlcBuildConfig.VLC_VERSION_NAME, userAgent()),
    )
    val scheduler: ScrobbleScheduler = WorkScrobbleScheduler(context)
    val store: ScrobbleStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomScrobbleStore(NagmedarDatabase.get(context).scrobbles()) }
    val flusher: ScrobbleFlusher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ScrobbleFlusher(store, settings, services) }
    val hub: ScrobbleHub by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ScrobbleHub(settings, store, services, scheduler, AppScope, Dispatchers.IO) }

    companion object {
        @Volatile
        private var instance: Scrobbling? = null

        fun get(context: Context): Scrobbling = instance ?: synchronized(this) {
            instance ?: Scrobbling(context.applicationContext).also { instance = it }
        }

        /** "Nagmedar/0.2.0 (+https://github.com/…)": who asks, as Last.fm and ListenBrainz ask of their clients. */
        fun userAgent(version: String = VlcBuildConfig.VLC_VERSION_NAME): String =
            "Nagmedar/$version (+https://github.com/hesaplarimdijital-coder/vlc-android)"
    }
}
