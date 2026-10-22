package app.nagmedar.ui.player.subtitles.providers

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import app.nagmedar.ui.BuildConfig
import app.nagmedar.ui.R
import app.nagmedar.ui.player.subtitles.OkHttpOpenSubtitles
import org.videolan.tools.Settings

/**
 * The app's subtitle providers (S-707), in one registry the search and Settings › Subtitles
 * share: OpenSubtitles first, then the user's own folder (off until it is turned on). A provider
 * is added by writing it here; none is loaded from elsewhere.
 */
object SubtitleProviders {
    @Volatile
    private var registry: ProviderRegistry? = null

    @Volatile
    private var folder: SubtitleFolderStore? = null

    fun registry(context: Context): ProviderRegistry = registry ?: synchronized(this) {
        // made in the global snapshot: a window's composition may ask first, other windows read it too
        registry ?: Snapshot.global { build(context.applicationContext) }.also { registry = it }
    }

    /** The folder the local provider searches. */
    fun folder(context: Context): SubtitleFolderStore {
        registry(context)
        return checkNotNull(folder)
    }

    private fun build(context: Context): ProviderRegistry {
        val prefs = Settings.getInstance(context)
        val store = SubtitleFolderStore(prefs).also { folder = it }
        val providers = listOf(
            OpenSubtitlesProvider(OkHttpOpenSubtitles(BuildConfig.OPENSUBTITLES_API_KEY)),
            LocalFolderProvider(DocumentLocalSubtitleFiles(context, store), context.getString(R.string.nag_prov_local_name)),
        )
        return ProviderRegistry(providers, PrefsProviderSettingsStore(prefs), offByDefault = setOf(LocalFolderProvider.ID))
    }
}
