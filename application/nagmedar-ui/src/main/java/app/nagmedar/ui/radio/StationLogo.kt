package app.nagmedar.ui.radio

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nagmedar.ui.covers.CoverImages
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * S-1105: the stations' logos (the directory's favicons) fetched with the app's User-Agent and kept
 * in memory decoded; one the server refuses (4xx), that is too large or that is no image is not
 * asked for again, one that failed for the network's sake (offline, a timeout, a 5xx) is asked for
 * again after a while ([LogoFailures]). What is read of an answer is bounded. Offline, in the
 * tests, nothing is fetched ([active] is on only in the app) and the monogram shows.
 */
object RadioLogos {

    /** The logos are fetched only in the app; tests and previews show the monograms. */
    @Volatile
    var active = false

    @Volatile
    private var userAgent = OkHttpRadioBrowser.userAgent(null)

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failed = LogoFailures()

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    }

    /** The User-Agent the logos are fetched with: the app's name and version. */
    fun identify(version: String?) {
        userAgent = OkHttpRadioBrowser.userAgent(version)
    }

    /** The logo at [url] decoded for [px] pixels; null when there is none to be had. */
    suspend fun load(url: String, px: Int): ImageBitmap? {
        if (!active || failed.blocked(url)) return null
        val key = "$url:$px"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val image = fetch(url, px)
                if (image == null) failed.never(url) else cache.put(key, image)
                image
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // the network's doing: ask again later
                failed.later(url)
                null
            } catch (e: IllegalArgumentException) {
                // not an address
                failed.never(url)
                null
            } catch (e: OutOfMemoryError) {
                // an image too big to decode here
                failed.never(url)
                null
            }
        }
    }

    /** The decoded logo; null when the answer is no logo (refused, too large, no image); an [IOException] when the network failed. */
    private fun fetch(url: String, px: Int): ImageBitmap? {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val bytes = client.newCall(request).execute().use { response ->
            if (response.code >= 500 || response.code == 429) throw IOException("HTTP ${response.code}")
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > MAX_LOGO_BYTES) return null
            readBounded(body.source(), MAX_LOGO_BYTES) ?: return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = CoverImages.sampleSize(bounds.outWidth, bounds.outHeight, px) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    /**
     * All of [source], at most [max] bytes: it is never read beyond one byte more than that (an
     * answer of no stated length may be endless); null when it is larger.
     */
    internal fun readBounded(source: BufferedSource, max: Long): ByteArray? {
        source.request(max + 1)
        if (source.buffer.size > max) return null
        return source.buffer.readByteArray()
    }

    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_LOGO_BYTES = 2L * 1024 * 1024
}

/**
 * The logos that could not be had. A refusal is for good ([never]); a failure of the network is
 * remembered for [retryAfterMs] only ([later]), so a start offline does not leave the stations
 * without logos until the app restarts.
 */
class LogoFailures(private val clock: () -> Long = System::currentTimeMillis, private val retryAfterMs: Long = RETRY_AFTER_MS) {
    private val until = HashMap<String, Long>()

    /** Whether [url] is not to be asked for now. */
    fun blocked(url: String): Boolean = synchronized(until) {
        val time = until[url] ?: return false
        if (clock() < time) return true
        until.remove(url)
        false
    }

    /** [url] is no logo (refused, too large, not an image): not asked for again. */
    fun never(url: String) {
        synchronized(until) { until[url] = Long.MAX_VALUE }
    }

    /** [url] could not be reached: asked for again after a while. */
    fun later(url: String) {
        synchronized(until) { if (until[url] != Long.MAX_VALUE) until[url] = clock() + retryAfterMs }
    }

    companion object {
        const val RETRY_AFTER_MS = 5 * 60_000L
    }
}

/** The letters a station's monogram shows: the initials of its first two words, upper case ("Kuzey FM" → "KF"). */
fun monogram(name: String, locale: Locale = Locale.ROOT): String {
    val words = name.trim().split(Regex("\\s+")).filter { word -> word.any { it.isLetterOrDigit() } }
    val letters = words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase(locale)
}

/** S-1105: a station's logo in a rounded tile, or its monogram where there is none. */
@Composable
fun StationLogo(station: Station, size: Dp, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val colors = NagTheme.colors
    val px = with(LocalDensity.current) { size.roundToPx() }
    val logo by produceState<ImageBitmap?>(null, station.logoUrl, px) {
        value = station.logoUrl?.let { RadioLogos.load(it, max(px, 1)) }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (logo == null) colors.surfaceRaised else colors.surface)
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = logo
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            val fontSize: TextUnit = (size.value * 0.34f).coerceAtLeast(11f).sp
            Text(monogram(station.name, currentLocale()), style = NagTheme.type.bodyStrong.copy(fontSize = fontSize), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
