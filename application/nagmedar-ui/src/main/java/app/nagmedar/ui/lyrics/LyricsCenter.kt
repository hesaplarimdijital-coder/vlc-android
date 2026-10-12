package app.nagmedar.ui.lyrics

import android.content.Context
import org.videolan.tools.NAGMEDAR_LYRICS_ONLINE
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig

/** S-1003: the app's one lyrics finder: the database's cache, the device's files, LRCLIB when the setting allows. */
object LyricsCenter {

    @Volatile
    private var finder: LyricsFinder? = null

    fun finder(context: Context): LyricsFinder = finder ?: synchronized(this) {
        finder ?: build(context.applicationContext).also { finder = it }
    }

    /** Whether LRCLIB may be asked (Settings › Lyrics); on unless turned off. */
    fun onlineAllowed(context: Context): Boolean = Settings.getInstance(context).getBoolean(NAGMEDAR_LYRICS_ONLINE, true)

    private fun build(context: Context): LyricsFinder = LyricsFinder(
        cache = DatabaseLyricsCache(context),
        local = DeviceLyrics(context),
        service = OkHttpLrcLib(OkHttpLrcLib.userAgent(BuildConfig.VLC_VERSION_NAME)),
        online = { onlineAllowed(context) },
    )
}
