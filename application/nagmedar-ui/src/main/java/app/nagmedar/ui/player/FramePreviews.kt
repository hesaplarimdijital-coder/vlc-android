package app.nagmedar.ui.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Small frames of the media playing, for the timeline's preview under the mouse (S-601). */
interface FramePreviews {
    /** The frame near [ms] (within [STEP_MS]); null when the media gives none: a stream, a file Android cannot read. */
    suspend fun frameAt(ms: Long): ImageBitmap?

    fun close()

    companion object {
        /** Frames are read and kept two seconds apart. */
        const val STEP_MS = 2_000L
    }
}

/**
 * Frames read by Android's MediaMetadataRetriever, from local files and content URIs; the last
 * few dozen are kept. A file it cannot read gives no frames, and is not asked again.
 */
class RetrieverFramePreviews(private val context: Context, private val uri: Uri) : FramePreviews {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<Long, ImageBitmap>(48)
    private var retriever: MediaMetadataRetriever? = null

    @Volatile
    private var failed = !readable(uri)

    override suspend fun frameAt(ms: Long): ImageBitmap? {
        if (failed) return null
        val key = ms.coerceAtLeast(0L) / FramePreviews.STEP_MS
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock { cache.get(key) ?: read(key * FramePreviews.STEP_MS)?.also { cache.put(key, it) } }
        }
    }

    private fun read(ms: Long): ImageBitmap? {
        if (failed) return null
        return try {
            val reader = retriever ?: MediaMetadataRetriever().also {
                it.setDataSource(context, uri)
                retriever = it
            }
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                reader.getScaledFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
            } else {
                reader.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            bitmap?.asImageBitmap()
        } catch (e: RuntimeException) {
            // IllegalArgumentException and friends: Android cannot read this file
            failed = true
            null
        }
    }

    override fun close() {
        scope.launch {
            lock.withLock {
                runCatching { retriever?.release() }
                retriever = null
                failed = true
            }
        }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180

        fun readable(uri: Uri): Boolean = uri.scheme in setOf(null, "file", "content")
    }
}
