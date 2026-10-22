package app.nagmedar.ui.library.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.covers.CoverImages
import app.nagmedar.ui.podcast.executeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Film information: the sizes of TMDB's images the screens ask for. */
enum class FilmImageSize(val tmdb: String) {
    /** A poster in a list row or a card. */
    Poster("w342"),

    /** A poster in the details. */
    PosterLarge("w500"),

    /** A backdrop behind the hero or the details. */
    Backdrop("w780"),

    /** An episode's still. */
    Still("w300"),
}

/**
 * Film information: TMDB's posters, backdrops and stills kept on the disk (the app's cache folder,
 * [MAX_DISK_BYTES] at most, the least recently shown going first) and, decoded, in memory. An
 * image is fetched only when [FilmInfoCenter] may use the network; offline the cached ones show.
 */
object FilmImages {
    private const val MAX_DISK_BYTES = 200L * 1024 * 1024
    private const val MAX_MEMORY_BYTES = 24 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

    private val memory = object : LruCache<String, ImageBitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val trimming = Mutex()

    fun folder(context: Context): File = File(context.cacheDir, "film-images")

    /** [path] (TMDB's "/abc.jpg") at [size], decoded for [px] pixels; null when it is not here and may not be fetched. */
    suspend fun load(context: Context, path: String, size: FilmImageSize, px: Int): ImageBitmap? {
        val name = fileName(path, size) ?: return null
        val key = "$name:$px"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(folder(context), name)
            if (!file.isFile) {
                if (!FilmInfoCenter.mayUseNetwork(context)) return@withContext null
                if (!download(context, path, size, file)) return@withContext null
            } else {
                // the least recently shown go first
                file.setLastModified(System.currentTimeMillis())
            }
            decode(file, px)?.also { memory.put(key, it) }
        }
    }

    /** What the images take on the disk, in bytes. */
    suspend fun diskBytes(context: Context): Long = withContext(Dispatchers.IO) {
        folder(context).listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        trimming.withLock { folder(context).listFiles()?.forEach { it.delete() } }
        memory.evictAll()
    }

    private suspend fun download(context: Context, path: String, size: FilmImageSize, file: File): Boolean {
        val request = Request.Builder().url(TmdbClient.imageUrl(size.tmdb, path)).header("User-Agent", FilmInfoCenter.userAgent()).build()
        val bytes = try {
            TmdbClient.defaultClient.newCall(request).executeCancellable { response ->
                if (!response.isSuccessful) return@executeCancellable null
                val body = response.body ?: return@executeCancellable null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@executeCancellable null
                body.bytes().takeIf { it.size <= MAX_IMAGE_BYTES }
            }
        } catch (e: IOException) {
            null
        } ?: return false
        return trimming.withLock {
            val dir = folder(context)
            if (!dir.isDirectory && !dir.mkdirs()) return@withLock false
            // written beside it and moved: a half written file is never read
            val part = File(dir, file.name + ".part")
            try {
                part.writeBytes(bytes)
                if (!part.renameTo(file)) {
                    part.delete()
                    return@withLock false
                }
            } catch (e: IOException) {
                part.delete()
                return@withLock false
            }
            trim(dir)
            true
        }
    }

    /** Takes the least recently shown images away until the folder holds [MAX_DISK_BYTES] at most. */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (old in files) {
            if (total <= MAX_DISK_BYTES) break
            total -= old.length()
            old.delete()
        }
    }

    private fun decode(file: File, px: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // not an image after all: it goes, and is fetched again later
            file.delete()
            return null
        }
        val options = BitmapFactory.Options().apply { inSampleSize = CoverImages.sampleSize(bounds.outWidth, bounds.outHeight, px) }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }

    /** "w342_abc.jpg" for TMDB's "/abc.jpg"; null for anything that is not a plain image name. */
    internal fun fileName(path: String, size: FilmImageSize): String? {
        val name = path.removePrefix("/")
        if (name.isEmpty() || !SAFE_NAME.matches(name)) return null
        return size.tmdb + "_" + name
    }

    private val SAFE_NAME = Regex("""[A-Za-z0-9_-]+\.(?:jpg|jpeg|png|webp)""")
}

/**
 * Film information: a TMDB image ([path] at [size], decoded for [px] pixels) filling [modifier], or
 * [fallback] while it loads, when there is none, and offline when it is not cached yet.
 */
@Composable
fun FilmImage(path: String?, size: FilmImageSize, px: Int, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, path, size, px) {
        value = if (path == null || !FilmInfoCenter.active) null else runCatching { FilmImages.load(context, path, size, px) }.getOrNull()
    }
    Box(modifier) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            fallback()
        }
    }
}
