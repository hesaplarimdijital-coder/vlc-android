package app.nagmedar.ui.music

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.audio.LoudnessScanner
import app.nagmedar.audio.MusicEngine
import app.nagmedar.audio.Waveform
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * S-1001: the tracks' waveforms, measured by the engine once (the whole file decoded, a few
 * seconds) and kept as small files in the cache, a few in memory. One file is measured at a time.
 */
object Waveforms {

    /** Buckets of a waveform: enough for a 4K window's timeline at a bar every three pixels. */
    const val POINTS = 720

    private val memory = LruCache<String, Waveform>(24)

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nagmedar-waveform").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()

    /** The waveform of [uri], measured now when there is none; null without the engine or when the file does not decode. */
    suspend fun of(context: Context, uri: String): Waveform? {
        memory.get(uri)?.let { return it }
        if (!MusicEngine.available) return null
        return withContext(worker) {
            memory.get(uri) ?: load(context.applicationContext, uri)?.also { memory.put(uri, it) }
        }
    }

    private fun load(context: Context, uri: String): Waveform? {
        val file = File(File(context.cacheDir, "waveforms"), "${hash(uri)}.bin")
        val source = Uri.parse(uri)
        val changed = source.path?.takeIf { source.scheme == null || source.scheme == "file" }?.let { File(it).lastModified() } ?: 0L
        if (file.isFile && file.lastModified() >= changed) {
            runCatching { Waveform.fromBytes(file.readBytes()) }.getOrNull()?.takeIf { it.size == POINTS }?.let { return it }
        }
        val measured = runCatching { LoudnessScanner.waveform(context, source, POINTS) }.getOrNull() ?: return null
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(measured.toBytes())
        }
        return measured
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** The waveform of [uri] once measured; null meanwhile, and for none. */
@Composable
fun rememberWaveform(uri: String?): Waveform? {
    val context = LocalContext.current
    return produceState<Waveform?>(null, uri) {
        value = uri?.let { runCatching { Waveforms.of(context, it) }.getOrNull() }
    }.value
}
