package app.nagmedar.ui.podcast

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.covers.Cover
import app.nagmedar.ui.covers.CoverCenter
import app.nagmedar.ui.covers.CoverFinder
import app.nagmedar.ui.covers.CoverImages
import app.nagmedar.ui.covers.CoverOrigin
import app.nagmedar.ui.covers.CoverStore
import app.nagmedar.ui.covers.fitForKeeping
import app.nagmedar.ui.covers.readAtMost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** The bytes of a show's cover at an address; null when there is none (404, not an image). Throws when the network fails. */
fun interface CoverDownload {
    suspend fun bytes(url: String): ByteArray?
}

/**
 * S-1101: shows' covers, which are web addresses (a feed's `itunes:image`, a search result's
 * artwork). Kept in the cover store of S-1006 ([CoverStore], [CoverImages] decode them for the size
 * a view needs), in a folder of their own in the app's cache: they can all be fetched again. An
 * address the server had no image for is not asked again for [MISS_RETRY_MS]; a network failure is
 * not remembered.
 */
class PodcastCoverFinder(
    private val store: CoverStore,
    private val download: CoverDownload,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The cover at [url]: the one kept, else fetched and kept; null when there is none. */
    suspend fun find(url: String): Cover? = withContext(io) {
        store.kept(url)?.let { return@withContext it }
        store.missedAt(url)?.let { if (clock() - it < MISS_RETRY_MS) return@withContext null }
        val bytes = try {
            download.bytes(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }
        val kept = bytes?.let { store.keep(url, CoverOrigin.Online, fitForKeeping(it)) }
        if (kept == null) store.miss(url, clock())
        kept
    }

    /** The file the cover at [url] is kept in, when it was fetched before; nothing is fetched. */
    fun kept(url: String): File? = store.kept(url)?.file

    companion object {
        /** An address with no image is asked about again after a week. */
        const val MISS_RETRY_MS = CoverFinder.MISS_RETRY_MS
    }
}

/** The app's podcast covers: fetched over OkHttp, four at a time, 12 MB at most each. */
object PodcastCovers {
    @Volatile
    private var finder: PodcastCoverFinder? = null

    private val gate = Semaphore(4)

    private val client by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

    private fun finder(context: Context): PodcastCoverFinder = finder ?: synchronized(this) {
        finder ?: PodcastCoverFinder(
            CoverStore(File(context.applicationContext.cacheDir, "podcast-covers")),
            download(PodcastCenter.userAgent()),
        ).also { finder = it }
    }

    private fun download(userAgent: String) = CoverDownload { url ->
        gate.withPermit {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.byteStream()?.use { it.readAtMost(MAX_BYTES) }
                    response.code in 400..499 -> null
                    else -> throw java.io.IOException("status ${response.code}")
                }
            }
        }
    }

    /** The cover at [url] decoded for [px] pixels; null when there is none, or in tests and previews (nothing asks the network there). */
    suspend fun image(context: Context, url: String, px: Int): ImageBitmap? {
        if (!CoverCenter.active) return null
        return withContext(Dispatchers.IO) { finder(context).find(url)?.let { CoverImages.load(it, px, shownAs = shownName(url, px)) } }
    }

    /** 1.0: the file the cover at [url] is kept in (Android Auto's artwork), when the app fetched it before; null otherwise. Off the main thread. */
    fun keptFile(context: Context, url: String): File? = finder(context).kept(url)

    /** The cover at [url] for [px] pixels when it was shown a moment ago and is still in memory; null otherwise. */
    fun shown(url: String, px: Int): ImageBitmap? = if (CoverCenter.active) CoverImages.shown(shownName(url, px)) else null

    private fun shownName(url: String, px: Int) = "podcast:$url:$px"

    private const val MAX_BYTES = 12 * 1024 * 1024
}

/** The cover at [url] for [px] pixels; null while it is fetched and when there is none. */
@Composable
fun rememberPodcastCover(url: String?, px: Int): ImageBitmap? {
    val context = LocalContext.current
    // shown a moment ago: there at once, and read again behind it
    val image by produceState(url?.let { PodcastCovers.shown(it, px) }, url, px) {
        value = url?.let { runCatching { PodcastCovers.image(context, it, px) }.getOrNull() }
    }
    return image
}
