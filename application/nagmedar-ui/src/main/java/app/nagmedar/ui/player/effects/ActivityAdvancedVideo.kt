package app.nagmedar.ui.player.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.nagmedar.ui.R
import app.nagmedar.ui.player.RetrieverFramePreviews
import app.nagmedar.ui.player.record.Recordings
import app.nagmedar.ui.player.subtitles.LibVlcRestart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.NagmedarVlcOptions
import org.videolan.resources.VLCInstance
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.ABRepeat
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The player's side of the advanced effects (S-610): a still frame for the preview, copied from
 * the video's surface; the logo's picture, copied into the app's cache as a PNG libVLC reads by its
 * path; and "Apply now": libVLC started again with the session's options, the film reopened where
 * it was.
 */
@Stable
internal class ActivityAdvancedVideo(private val activity: VideoPlayerActivity) {
    /** The frame the preview draws on; null when none could be taken (a stream, a protected film). */
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Where [frame] comes from. */
    var source by mutableStateOf(PreviewSource.Sample)
        private set

    /** The logo as the preview draws it. */
    var logo by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The picked picture could not be read. */
    var logoFailed by mutableStateOf(false)
        private set

    /** The film is being reopened with the new options. */
    var applying by mutableStateOf(false)
        private set

    /** The playing film's height, for the text's size; 0 when not known. */
    fun pictureHeight(): Int = runCatching { activity.service?.currentVideoTrack?.getHeight() }.getOrNull() ?: 0

    /**
     * "Apply now" can reopen the film: libVLC plays one on this screen (not cast), and nothing is
     * being recorded (a reopen would end it).
     */
    fun canReopen(): Boolean {
        val service = activity.service ?: return false
        return service.canRecord && Recordings.recorder?.recording != true && !activity.displayManager.isOnRenderer
    }

    /**
     * The frame for the preview: read from the file at the time playing, before any effect; else
     * copied from the screen while no effect shows there; else none (the sample picture).
     */
    suspend fun grabFrame() {
        val service = activity.service ?: return
        val uri = service.currentMediaWrapper?.uri
        val raw = uri?.let { RetrieverFramePreviews(activity.applicationContext, it) }?.let { previews ->
            try {
                previews.frameAt(service.getTime())
            } finally {
                previews.close()
            }
        }
        val inForce = PreviewSource.inForce(LibVlcVideoOptions.started(), NagmedarVlcOptions.startedTransform)
        val screen = if (raw == null && !inForce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching { copyFrame() }.getOrNull()?.asImageBitmap() else null
        source = PreviewSource.choose(raw != null, screen != null, inForce)
        frame = raw ?: screen
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun copyFrame(): Bitmap? {
        val layout = activity.videoLayout ?: return null
        val surface = layout.findViewById<SurfaceView>(R.id.surface_video)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
        if (surface != null) {
            val scale = FRAME_WIDTH.toFloat() / surface.width
            val bitmap = Bitmap.createBitmap(FRAME_WIDTH, (surface.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val copied = suspendCancellableCoroutine { done ->
                try {
                    PixelCopy.request(surface, bitmap, { result -> if (done.isActive) done.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
                } catch (e: IllegalArgumentException) {
                    done.resume(false)
                }
            }
            return bitmap.takeIf { copied }
        }
        val texture = layout.findViewById<TextureView>(R.id.texture_video)?.takeIf { it.isShown && it.isAvailable } ?: return null
        return texture.getBitmap(FRAME_WIDTH, (texture.height * FRAME_WIDTH.toFloat() / texture.width.coerceAtLeast(1)).roundToInt().coerceAtLeast(1))
    }

    /** The logo of [video], read again for the preview (after the player came back). */
    suspend fun loadLogo(video: AdvancedVideo) {
        val path = video.logo.file ?: return
        if (logo != null) return
        logo = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }?.asImageBitmap()
    }

    /** The picked picture copied as a PNG, at most [LOGO_MAX] pixels long; its path and name, or null. */
    suspend fun pickedLogo(uri: Uri): Pair<String, String>? {
        logoFailed = false
        // the pictures libVLC reads now, or will at its next start, stay
        val inUse = (NagmedarVlcOptions.startedVideo.options + NagmedarVlcOptions.sessionVideo.options)
            .filter { it.startsWith(LOGO_FILE) }
            .map { it.removePrefix(LOGO_FILE) }
            .toSet()
        val copied = withContext(Dispatchers.IO) { runCatching { copyLogo(activity.applicationContext, uri, inUse) }.getOrNull() }
        if (copied == null) {
            logoFailed = true
            return null
        }
        logo = copied.second.asImageBitmap()
        return copied.first.path to (displayName(uri) ?: copied.first.name)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /**
     * "Apply now": libVLC starts again with the options as they are now, a new player is made from
     * it and the video's surfaces go over to it. The film opens again paused, goes to where it was
     * once it can, gets back its audio and subtitle tracks (both subtitles, S-703), delays, speed and
     * A-B loop, and plays on if it played. True when it reopened.
     */
    suspend fun reopen(): Boolean {
        val service = activity.service ?: return false
        if (!canReopen() || applying) return false
        applying = true
        try {
            val manager = service.playlistManager
            val old = service.mediaplayer
            val state = PlayState(
                index = service.currentMediaPosition,
                timeMs = service.getTime(),
                playing = service.isPlaying,
                scale = old.videoScale,
                audio = runCatching { old.getSelectedTrack(IMedia.Track.Type.Audio)?.id }.getOrNull(),
                texts = runCatching { old.getSelectedTracks(IMedia.Track.Type.Text)?.map { it.id } }.getOrNull().orEmpty(),
                audioDelay = service.audioDelay,
                spuDelay = service.spuDelay,
                rate = service.rate,
                ab = manager.abRepeat.value?.let { ABRepeat(it.start, it.stop) },
                abOn = manager.abRepeatOn.value == true,
            )
            // the tracks and delays as VLC keeps them for a film of the library
            service.saveMediaMeta()
            runCatching { old.detachViews() }
            VLCInstance.restart()
            LibVlcRestart.pending = false
            service.restartMediaPlayer()
            val player = service.mediaplayer
            activity.videoLayout?.let { player.attachViews(it, activity.displayManager, true, false) }
            player.videoScale = state.scale
            // paused (":start-paused"): nothing is heard before it is where it was
            awaitEvent(service, setOf(MediaPlayer.Event.Paused, MediaPlayer.Event.Playing)) {
                manager.playIndex(state.index, MediaWrapper.MEDIA_PAUSED, forceResume = true)
            }
            // a film may say it can seek a little after it opened
            if (state.timeMs > 0) {
                withTimeoutOrNull(SEEKABLE_TIMEOUT_MS) { while (!service.isSeekable) delay(POLL_MS) }
                if (service.isSeekable) service.seek(state.timeMs, fromUser = true)
            }
            restore(state)
            if (state.playing) {
                // the library's own tracks and delays come back as it plays (its Playing): ours again over them
                awaitEvent(service, setOf(MediaPlayer.Event.Playing)) { service.play() }
                restore(state)
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not reopen the film", e)
            return false
        } finally {
            applying = false
        }
    }

    /**
     * Does [action] and waits, at most [OPEN_TIMEOUT_MS], for libVLC to say one of [types] (as the
     * playback service passes its events on, after the playlist did its part); false when it did not.
     */
    private suspend fun awaitEvent(service: PlaybackService, types: Set<Int>, action: suspend () -> Unit): Boolean {
        val heard = CompletableDeferred<Unit>()
        val callback = object : PlaybackService.Callback {
            override fun update() = Unit

            override fun onMediaEvent(event: IMedia.Event) = Unit

            override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
                if (event.type in types) heard.complete(Unit)
            }
        }
        service.addCallback(callback)
        return try {
            action()
            withTimeoutOrNull(OPEN_TIMEOUT_MS) { heard.await() } != null
        } finally {
            service.removeCallback(callback)
        }
    }

    /** What the film had before it reopened. */
    private class PlayState(
        val index: Int,
        val timeMs: Long,
        val playing: Boolean,
        val scale: MediaPlayer.ScaleType,
        val audio: String?,
        val texts: List<String>,
        val audioDelay: Long,
        val spuDelay: Long,
        val rate: Float,
        val ab: ABRepeat?,
        val abOn: Boolean,
    )

    /** The tracks, once the new player has them, the delays, the speed, the A-B loop. */
    private suspend fun restore(state: PlayState) {
        val service = activity.service ?: return
        val player = service.mediaplayer
        withTimeoutOrNull(TRACKS_TIMEOUT_MS) {
            while (runCatching { player.getTracks(IMedia.Track.Type.Audio).isNullOrEmpty() }.getOrDefault(true) && state.audio != null) delay(POLL_MS)
        }
        runCatching {
            state.audio?.let { player.selectTracks(IMedia.Track.Type.Audio, it) }
            if (state.texts.isEmpty()) player.unselectTrackType(IMedia.Track.Type.Text) else player.selectTracks(IMedia.Track.Type.Text, state.texts.joinToString(","))
        }
        if (service.audioDelay != state.audioDelay) service.setAudioDelay(state.audioDelay)
        if (service.spuDelay != state.spuDelay) service.setSpuDelay(state.spuDelay)
        if (service.rate != state.rate) service.setRate(state.rate, false)
        val manager = service.playlistManager
        if (state.ab != null) manager.abRepeat.value = ABRepeat(state.ab.start, state.ab.stop)
        if (manager.abRepeatOn.value != state.abOn) manager.abRepeatOn.value = state.abOn
    }

    private companion object {
        const val TAG = "Nagmedar/Effects"
        const val LOGO_FILE = "--logo-file="
        const val FRAME_WIDTH = 640
        const val LOGO_MAX = 512
        const val OPEN_TIMEOUT_MS = 10_000L
        const val SEEKABLE_TIMEOUT_MS = 5_000L
        const val TRACKS_TIMEOUT_MS = 3_000L
        const val POLL_MS = 100L

        /** The picture read (sampled down to about [LOGO_MAX]) and written as a PNG into the cache's overlays. */
        fun copyLogo(context: Context, uri: Uri, inUse: Set<String>): Pair<File, Bitmap>? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LOGO_MAX) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
            val longest = max(decoded.width, decoded.height)
            val bitmap = if (longest > LOGO_MAX) {
                val factor = LOGO_MAX.toFloat() / longest
                Bitmap.createScaledBitmap(decoded, (decoded.width * factor).roundToInt().coerceAtLeast(1), (decoded.height * factor).roundToInt().coerceAtLeast(1), true)
            } else {
                decoded
            }
            val folder = File(context.cacheDir, "overlays").apply { mkdirs() }
            // a new name each time: libVLC and the preview read the new picture; the old ones go once nothing reads them
            folder.listFiles()?.filter { it.path !in inUse }?.forEach { it.delete() }
            val file = File(folder, "logo-${System.currentTimeMillis()}.png")
            file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) return null }
            return file to bitmap
        }
    }
}
