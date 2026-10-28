package app.nagmedar.ui.covers

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nagmedar.audio.FileTags
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.library.siblingFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.NAGMEDAR_COVERS_ONLINE
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import org.videolan.tools.Notices

/** S-1006: the app's one cover finder, and the word that a cover changed. */
object CoverCenter {

    @Volatile
    private var finder: CoverFinder? = null

    private val changeCount = MutableStateFlow(0L)

    /** Grows when a cover is chosen, taken away or looked for again: the views read theirs again. */
    val changes: StateFlow<Long> = changeCount

    /**
     * Covers are looked for only in the app: its activity turns this on. Tests and previews show
     * the music note, and never ask the network.
     */
    @Volatile
    var active = false

    fun finder(context: Context): CoverFinder = finder ?: synchronized(this) {
        finder ?: build(context.applicationContext).also { finder = it }
    }

    /** Whether Cover Art Archive may be asked (Settings › Covers); on unless turned off. */
    fun onlineAllowed(context: Context): Boolean = Settings.getInstance(context).getBoolean(NAGMEDAR_COVERS_ONLINE, true)

    fun changed() {
        changeCount.value++
    }

    /** [track]'s cover decoded for [px] pixels; null when there is none. Reads files and the network. */
    suspend fun image(context: Context, track: CoverTrack, px: Int): ImageBitmap? {
        if (!active) return null
        return withContext(Dispatchers.IO) { finder(context).find(track)?.let { CoverImages.load(it, px, shownAs = "${track.key}:$px") } }
    }

    /**
     * [track]'s cover for [px] pixels when it was shown a moment ago and is still decoded in memory; null otherwise.
     * Read on the main thread, so a screen that comes back has its covers in its first frame.
     */
    fun shown(track: CoverTrack, px: Int): ImageBitmap? = if (active) CoverImages.shown("${track.key}:$px") else null

    private fun build(context: Context): CoverFinder = CoverFinder(
        store = CoverStore { File(context.filesDir, "covers") },
        sources = DeviceCoverSources(context),
        service = CoverArtArchive(CoverArtArchive.userAgent(BuildConfig.VLC_VERSION_NAME)),
        online = { onlineAllowed(context) },
    )
}

/** S-1006: the covers decoded, by file, its version and the size asked; a few dozen megabytes of them. */
object CoverImages {
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Which decoded image a view last showed for its name ([load]'s `shownAs`): the key into [cache]. */
    private val shownKeys = LruCache<String, String>(MAX_SHOWN)

    /**
     * [cover] decoded no smaller than [px] on its shorter side where it is that large; null when it does not decode.
     * [shownAs] names what a view shows it for (a track's album, a podcast), so [shown] finds it again at once.
     */
    fun load(cover: Cover, px: Int, shownAs: String? = null): ImageBitmap? {
        val key = "${cover.file.path}:${cover.version}:$px"
        if (shownAs != null) shownKeys.put(shownAs, key)
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cover.file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, px) }
        val bitmap = BitmapFactory.decodeFile(cover.file.path, options) ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    /** What [load] last gave for [shownAs], when it is still in memory; nothing is read from the disk. */
    fun shown(shownAs: String): ImageBitmap? = shownKeys.get(shownAs)?.let { cache.get(it) }

    /** The largest power of two that keeps the shorter side at [px] or more. */
    internal fun sampleSize(width: Int, height: Int, px: Int): Int {
        var sample = 1
        while (min(width, height) / (sample * 2) >= max(1, px)) sample *= 2
        return sample
    }

    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_SHOWN = 2048
}

/**
 * S-1006: the device's covers: the file's tags (through the music engine), an image beside it
 * (cover, folder, front, album, AlbumArt…, else the folder's only image), the MusicBrainz ids its
 * tags give. A local folder is listed directly, a picked one (S-309) through its document tree.
 */
class DeviceCoverSources(context: Context) : CoverSources {
    private val context = context.applicationContext

    override fun tags(track: CoverTrack): ByteArray? = FileTags.cover(context, Uri.parse(track.uri))?.takeIf(::looksLikeImage)

    override fun folder(track: CoverTrack): ByteArray? {
        val media = Uri.parse(track.uri)
        val images: List<Pair<String, Uri>> = when (media.scheme) {
            null, ContentResolver.SCHEME_FILE -> media.path?.let(::File)?.parentFile?.listFiles()
                ?.filter { it.isFile && isImageName(it.name) }
                ?.map { it.name to Uri.fromFile(it) }
                .orEmpty()
            ContentResolver.SCHEME_CONTENT -> siblingFiles(context, media).filter { (name, _) -> isImageName(name) }.map { (name, uri) -> name to Uri.parse(uri) }
            else -> emptyList()
        }
        val chosen = folderCover(images.map { it.first }) ?: return null
        val uri = images.first { it.first == chosen }.second
        return read(uri)?.let(::fitForKeeping)?.takeIf(::looksLikeImage)
    }

    override fun ids(track: CoverTrack): MusicBrainzIds {
        val uri = Uri.parse(track.uri)
        return MusicBrainzIds(
            release = FileTags.tag(context, uri, "MUSICBRAINZ_ALBUMID")?.takeIf { MBID.matches(it) },
            releaseGroup = FileTags.tag(context, uri, "MUSICBRAINZ_RELEASEGROUPID")?.takeIf { MBID.matches(it) },
        )
    }

    private fun read(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_READ_BYTES) }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val NAMES = listOf("cover", "folder", "front", "album", "albumart", "artwork", "thumb")
        private val MBID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** A scanned cover is read up to this much; larger ones are made smaller before they are kept. */
        private const val MAX_READ_BYTES = 32 * 1024 * 1024

        internal fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

        /** The folder's cover among its image files' [names]: by its name, else the only image. */
        internal fun folderCover(names: List<String>): String? {
            val images = names.filter(::isImageName)
            for (wanted in NAMES) {
                images.firstOrNull { it.substringBeforeLast('.').lowercase(Locale.ROOT) == wanted }?.let { return it }
            }
            // Windows Media Player's: AlbumArt_{…}_Large.jpg
            images.filter { it.lowercase(Locale.ROOT).startsWith("albumart") }.maxByOrNull { it.lowercase(Locale.ROOT).contains("large") }?.let { return it }
            return images.singleOrNull()
        }
    }
}

/** A very large image (a scanned sleeve) made smaller: no side over 2000 pixels, as JPEG; the rest as they are. */
internal fun fitForKeeping(bytes: ByteArray): ByteArray {
    if (bytes.size <= KEEP_BYTES) return bytes
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return bytes
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= KEEP_SIDE) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return bytes
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

private const val KEEP_BYTES = 4 * 1024 * 1024
private const val KEEP_SIDE = 2_000
private const val JPEG_QUALITY = 90

/** At most [limit] bytes of the stream; null when it is longer. */
internal fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (out.size() + count > limit) return null
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

// --------------------------------------------------------------- the views'

/** S-1006: [track]'s cover for [px] pixels, read again when a cover changes; null while looked for, or when there is none. */
@Composable
fun rememberCover(track: CoverTrack?, px: Int): ImageBitmap? {
    val context = LocalContext.current
    val changes by CoverCenter.changes.collectAsState()
    // shown a moment ago: there at once, and read again behind it
    val image by produceState(track?.let { CoverCenter.shown(it, px) }, track, px, changes) {
        value = track?.let { runCatching { CoverCenter.image(context, it, px) }.getOrNull() }
    }
    return image
}

/**
 * S-1006: the cover menu of [track]'s album: "Kapağı değiştir…" (an image from the system's
 * picker), "Kapağı yeniden ara", and "Seçilen kapağı kaldır" when one was chosen.
 */
@Composable
fun rememberCoverMenu(track: CoverTrack?): () -> List<MenuEntry> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val change = stringResource(R.string.nag_cover_change)
    val again = stringResource(R.string.nag_cover_again)
    val unchoose = stringResource(R.string.nag_cover_unchoose)
    val changes by CoverCenter.changes.collectAsState()
    val chosen by produceState(false, track, changes) {
        value = track?.let { CoverCenter.finder(context).isChosen(it) } ?: false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || track == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_CHOSEN_BYTES) }
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }
            val cover = bytes?.let { CoverCenter.finder(context).choose(track, withContext(Dispatchers.IO) { fitForKeeping(it) }) }
            if (cover == null) Notices.show(context, R.string.nag_cover_not_image) else CoverCenter.changed()
        }
    }
    return {
        if (track == null) {
            emptyList()
        } else {
            listOfNotNull(
                MenuEntry.Item(change, { picker.launch("image/*") }),
                MenuEntry.Item(again, {
                    scope.launch {
                        CoverCenter.finder(context).again(track)
                        CoverCenter.changed()
                    }
                }),
                if (chosen) {
                    MenuEntry.Item(unchoose, {
                        scope.launch {
                            CoverCenter.finder(context).unchoose(track)
                            CoverCenter.changed()
                        }
                    })
                } else {
                    null
                },
            )
        }
    }
}

/** A cover chosen by hand may be large: a scan of the sleeve. */
private const val MAX_CHOSEN_BYTES = 32 * 1024 * 1024
