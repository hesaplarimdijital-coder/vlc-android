package app.nagmedar.ui.player.effects

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import app.nagmedar.ui.player.LibVlcExtensions
import app.nagmedar.ui.player.snapshot.displaySize
import app.nagmedar.ui.player.snapshot.fitPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.videolan.libvlc.interfaces.IMedia
import app.nagmedar.ui.R
import org.videolan.vlc.gui.video.VideoPlayerActivity
import kotlin.coroutines.resume

/**
 * The black bars found in the playing film and cut away (S-505's auto crop): a few small copies
 * of the picture are taken from the video's surface over some seconds, the bars every one of
 * them has are measured, and libVLC crops that many pixels off the top and the bottom. A film
 * that starts while the crop is on is read again; off, the picture shows whole.
 */
internal class ActivityAutoCrop(private val activity: VideoPlayerActivity, private val scope: CoroutineScope) : AutoCropTarget {
    private var enabled = false
    private var job: Job? = null

    /** What was cut from the playing film, in its pixels: top and bottom. */
    var bars: BlackBars? = null
        private set

    override fun setAutoCrop(enabled: Boolean) {
        this.enabled = enabled
        job?.cancel()
        if (enabled) {
            job = scope.launch { measure() }
        } else {
            bars = null
            activity.service?.mediaplayer?.let { LibVlcExtensions.clearCrop(it) }
        }
    }

    /** A film started: with the crop on, its bars are read anew. */
    fun onMediaStarted() {
        if (!enabled) return
        bars = null
        job?.cancel()
        job = scope.launch {
            delay(FIRST_SAMPLE_MS)
            measure()
        }
    }

    private suspend fun measure() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val frames = ArrayList<BlackBars>()
        var pictureHeight = 0
        repeat(SAMPLES) { index ->
            if (index > 0) delay(SAMPLE_GAP_MS)
            val sample = sample() ?: return@repeat
            pictureHeight = sample.first
            BlackBarsFinder.find(sample.second, SAMPLE_WIDTH, SAMPLE_HEIGHT)?.let { frames.add(it) }
        }
        val found = BlackBarsFinder.agree(frames) ?: return
        val inPicture = BlackBarsFinder.scale(found, SAMPLE_HEIGHT, pictureHeight)
        val player = activity.service?.mediaplayer ?: return
        bars = inPicture
        if (inPicture.isNone) LibVlcExtensions.clearCrop(player) else LibVlcExtensions.setCropBorder(player, 0, 0, inPicture.top, inPicture.bottom)
    }

    /** The picture's height in its own pixels, and a small copy of it: its brightness, row by row. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private suspend fun sample(): Pair<Int, IntArray>? {
        val service = activity.service ?: return null
        val layout = activity.videoLayout ?: return null
        val surface = layout.findViewById<SurfaceView>(R.id.surface_video)?.takeIf { it.isShown && it.width > 0 && it.height > 0 } ?: return null
        val track = service.mediaplayer.getSelectedTrack(IMedia.Track.Type.Video) as? IMedia.VideoTrack ?: return null
        if (track.width <= 0 || track.height <= 0) return null
        val native = displaySize(track.width, track.height, track.sarNum, track.sarDen, turned = track.orientation >= IMedia.VideoTrack.Orientation.LeftTop)
        val whole = surface.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT
        val inView = if (whole) fitPicture(IntSize(surface.width, surface.height), native.width.toDouble() / native.height) else IntRect(0, 0, surface.width, surface.height)
        val source = if (whole) Rect(inView.left, inView.top, inView.right, inView.bottom) else null
        val small = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
        if (!copy(surface, source, small)) return null
        val pixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        small.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        small.recycle()
        return native.height to BlackBarsFinder.luma(pixels)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.N)
    private suspend fun copy(view: SurfaceView, source: Rect?, into: Bitmap): Boolean = suspendCancellableCoroutine { done ->
        try {
            PixelCopy.request(view, source, into, { result -> if (done.isActive) done.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            // the surface went away
            if (done.isActive) done.resume(false)
        }
    }

    private companion object {
        /** A narrow, tall copy: the bars are rows, and 216 of them are 5 pixels of a 1080p picture each. */
        const val SAMPLE_WIDTH = 64
        const val SAMPLE_HEIGHT = 216
        const val SAMPLES = 5
        const val SAMPLE_GAP_MS = 1500L
        const val FIRST_SAMPLE_MS = 1000L
    }
}
