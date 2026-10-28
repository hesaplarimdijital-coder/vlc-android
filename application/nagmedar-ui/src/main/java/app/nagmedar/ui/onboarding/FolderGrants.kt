package app.nagmedar.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.ui.convert.ConvertOptions
import app.nagmedar.ui.convert.ConvertStore
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.podcast.PodcastSettingsStore
import app.nagmedar.ui.radio.record.RecordingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.Settings
import java.util.Locale

/**
 * What in the app keeps a folder the system's picker gave (its tree, or files inside it), and whether it writes
 * there. One folder can serve several at once (a library section, the recordings, the converter,
 * a podcast's downloads…), so none of them lets go of its permission while another still needs it ([FolderGrants]).
 */
enum class GrantHolder(val writes: Boolean) {
    /** The folders the library's sections show (Settings › Library). */
    LibrarySections(false),

    /** S-309: the folders picked without media access. */
    PickedFolders(false),

    /** The converter's folder. */
    Converter(true),

    /** The converter's jobs not finished yet, which write into their folder. */
    ConvertJobs(true),

    /** S-1107: the radio's recordings. */
    RadioRecordings(true),

    /** S-1103: the podcasts' download folder, and the episodes downloaded into a picked one (the cleaner deletes them). */
    PodcastDownloads(true),

    /** S-1206, S-1210: the TV Lab's batch folders, and its remux jobs' films and new files. */
    TvLab(true),

    /** S-1005: the folders the lyrics editor was given to write .lrc files into. */
    Lyrics(true),
}

/**
 * The one check of whether a persisted folder permission is still in use, and the one way to let it go: every
 * setting that gives up a folder calls [release] once its new value is stored, and the permission goes only as far
 * as no [GrantHolder] needs it (reading stays while any holder keeps the folder, writing while one that writes does).
 */
object FolderGrants {
    const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
    const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private const val KEPT_PREFIX = "nagmedar_kept_trees_"

    /** The holders that keep no setting of their folders: the folders they were given are noted under these keys. */
    private val noted = listOf(GrantHolder.TvLab, GrantHolder.Lyrics)

    fun keptKey(holder: GrantHolder): String = KEPT_PREFIX + holder.name.lowercase(Locale.ROOT)

    /** This device's folders, which a backup leaves out. */
    val keptKeys: Set<String> = noted.map(::keptKey).toSet()

    /** The holders that keep [uri]: it, or a file inside it (a tree's document, "…/tree/X/document/Y"). */
    fun holdersOf(uri: String, uses: Map<GrantHolder, Collection<String>>): Set<GrantHolder> =
        uses.filterValues { kept -> kept.any { it == uri || it.startsWith("$uri/document/") } }.keys

    /** Which of [modes] ([READ], [WRITE]) [uri] can let go of while [uses] keep what they keep. */
    fun releasable(uri: String, modes: Int, uses: Map<GrantHolder, Collection<String>>): Int {
        val holders = holdersOf(uri, uses)
        var needed = 0
        if (holders.isNotEmpty()) needed = needed or READ
        if (holders.any { it.writes }) needed = needed or WRITE
        return modes and needed.inv()
    }

    /** What the holders keep by the preferences: the settings' folders, and the folders noted for those without one. */
    fun preferenceUses(prefs: SharedPreferences): Map<GrantHolder, Set<String>> = mapOf(
        GrantHolder.LibrarySections to LibrarySources.trees(prefs),
        GrantHolder.PickedFolders to prefs.getStringSet(NAGMEDAR_SAF_FOLDERS, null).orEmpty(),
        GrantHolder.Converter to setOfNotNull(prefs.getString(ConvertOptions.KEY_FOLDER, null)),
        GrantHolder.RadioRecordings to setOfNotNull(prefs.getString(RecordingPrefs.KEY_FOLDER, null)),
        GrantHolder.PodcastDownloads to setOfNotNull(prefs.getString(PodcastSettingsStore.KEY_FOLDER, null)),
    ).plus(noted.associateWith { prefs.getStringSet(keptKey(it), null).orEmpty() })
        .mapValues { (_, uris) -> uris.filter { it.isNotBlank() }.toSet() }

    /**
     * What every holder keeps now: [preferenceUses], the converter's unfinished jobs, the podcast episodes downloaded
     * into a picked folder and the TV Lab's remux jobs (the history's too: a job can be run again). Off the main thread.
     */
    fun uses(context: Context): Map<GrantHolder, Set<String>> {
        val stored = preferenceUses(Settings.getInstance(context))
        val db = NagmedarDatabase.get(context)
        val remuxed = db.remuxJobs().all().flatMap { listOf(it.inputUri, it.outputUri) }
        return stored + mapOf(
            GrantHolder.ConvertJobs to ConvertStore(context).activeFolders(),
            GrantHolder.PodcastDownloads to stored.getValue(GrantHolder.PodcastDownloads) + db.podcasts().downloadedDocuments(),
            GrantHolder.TvLab to stored.getValue(GrantHolder.TvLab) + remuxed,
        )
    }

    /** [uri] was given to [holder], which keeps no setting of it (the TV Lab's batch, the lyrics editor): the others leave it. */
    fun note(context: Context, holder: GrantHolder, uri: String) {
        if (holder !in noted) return
        val prefs = Settings.getInstance(context)
        val key = keptKey(holder)
        val kept = prefs.getStringSet(key, null).orEmpty()
        if (uri !in kept) prefs.edit().putStringSet(key, kept + uri).apply()
    }

    /**
     * Lets go of the [modes] of [uri]'s permission that nothing in the app needs any more; called by the setting that
     * held it once its new value is stored. In the background, as the jobs and the database are read; when they cannot
     * be, nothing is let go.
     */
    fun release(context: Context, uri: String, modes: Int = READ or WRITE) {
        if (uri.isBlank()) return
        val app = context.applicationContext
        AppScope.launch(Dispatchers.IO) {
            val free = runCatching { releasable(uri, modes, uses(app)) }.getOrDefault(0)
            if (free != 0) runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(uri), free) }
        }
    }
}
