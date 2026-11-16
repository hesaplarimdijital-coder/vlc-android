/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: after Metamatik (core/data's AndroidImageSampler and the HTTP clients of its lookup modules), a fork
 * of Metadator, under the AGPL-3.0; written again on OkHttp.
 */
package app.nagmedar.ui.tags

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.tags.cover.FindCoversUseCase
import app.nagmedar.tags.cover.ImageSample
import app.nagmedar.tags.cover.ImageSampler
import app.nagmedar.tags.lookup.ArtworkDownloader
import app.nagmedar.tags.lookup.DeezerProvider
import app.nagmedar.tags.lookup.DownloadedImage
import app.nagmedar.tags.lookup.FindAlbumUseCase
import app.nagmedar.tags.lookup.ITunesProvider
import app.nagmedar.tags.lookup.LookupService
import app.nagmedar.tags.lookup.MetadataProvider
import app.nagmedar.tags.lookup.MusicBrainzProvider
import app.nagmedar.tags.lookup.WebGetter
import app.nagmedar.tags.lookup.WebReply
import app.nagmedar.ui.covers.CoverArtArchive
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.videolan.vlc.BuildConfig

/**
 * 1.3.0: the catalogues the tag editor asks (MusicBrainz, Deezer, iTunes; Settings › Etiket düzenleyici turns each
 * on or off), the covers they have, and the pictures downloaded from them, through OkHttp. What leaves the device is
 * what is searched for: a song's title, artist and album.
 */
object TagLookups {
    @Volatile
    private var built: TagLookupTools? = null

    /** The app's tools, made at the first lookup. */
    fun tools(context: Context): TagLookupTools = built ?: synchronized(this) {
        built ?: build(context.applicationContext).also { built = it }
    }

    /** The catalogues, by id and name, in the order Settings lists them. */
    val catalogues: List<Pair<String, String>> = listOf(
        MusicBrainzProvider.ID to "MusicBrainz",
        DeezerProvider.ID to "Deezer",
        ITunesProvider.ID to "iTunes",
    )

    private fun build(context: Context): TagLookupTools {
        val web = OkHttpWebGetter(client, CoverArtArchive.userAgent(BuildConfig.VLC_VERSION_NAME))
        val providers: List<MetadataProvider> = listOf(MusicBrainzProvider(web), DeezerProvider(web), ITunesProvider(web))
        val service = LookupService(providers) { TagSettings.catalogues(context) }
        val downloader = OkHttpArtworkDownloader(client)
        return TagLookupTools(service, FindCoversUseCase(service, downloader, AndroidImageSampler()), FindAlbumUseCase(service), downloader)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** 1.3.0: what the editor looks things up with: songs, covers, albums, and the pictures' downloads. */
class TagLookupTools(
    val service: LookupService,
    val covers: FindCoversUseCase,
    val albums: FindAlbumUseCase,
    val downloader: ArtworkDownloader,
)

/** The tests' lookups (fakes); the app's are [TagLookups]'. */
val LocalTagLookups = staticCompositionLocalOf<TagLookupTools?> { null }

@Composable
fun rememberTagLookups(): TagLookupTools = LocalTagLookups.current ?: TagLookups.tools(LocalContext.current)

/** 1.3.0: a GET through OkHttp, saying who asks (MusicBrainz turns away those who do not). */
class OkHttpWebGetter(private val client: OkHttpClient, private val userAgent: String) : WebGetter {
    override suspend fun get(url: String): WebReply = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response -> WebReply(response.code, response.body?.string().orEmpty()) }
    }
}

/** 1.3.0: a cover downloaded whole (20 MB at most), or its first bytes, to read how large it is. */
class OkHttpArtworkDownloader(private val client: OkHttpClient) : ArtworkDownloader {
    override suspend fun download(url: String): DownloadedImage? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_BYTES) return@use null
                val bytes = body.bytes().takeIf { it.size <= MAX_BYTES } ?: return@use null
                val mime = body.contentType()?.toString()?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: sniffImage(bytes) ?: return@use null
                DownloadedImage(bytes, mime)
            }
        }.getOrNull()
    }

    override suspend fun peek(url: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Range", "bytes=0-${maxBytes - 1}").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val source = response.body?.byteStream() ?: return@use null
                val buffer = ByteArray(maxBytes)
                var read = 0
                while (read < maxBytes) {
                    val n = source.read(buffer, read, maxBytes - read)
                    if (n < 0) break
                    read += n
                }
                buffer.copyOf(read)
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}

/** 1.3.0: pictures sampled with the device's decoders: whatever Android shows, it samples. */
class AndroidImageSampler : ImageSampler {
    override suspend fun sample(bytes: ByteArray): ImageSample? = withContext(Dispatchers.Default) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null
            // decoded only as large as the sample needs: a 3000 px cover whole is 36 MB
            val options = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, minOf(width, height) / DECODED_EDGE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@runCatching null
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ImageSample.of(width, height, pixels, columns = bitmap.width)
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    private companion object {
        /** Several pixels for each cell of the sample, so that what is averaged is the picture. */
        const val DECODED_EDGE = ImageSample.SIZE * 4
    }
}
