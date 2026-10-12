package app.nagmedar.ui.player.snapshot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.nagmedar.ui.R
import app.nagmedar.ui.library.TechRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.Permissions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The player's screenshots (S-606). The picture is copied from the video's surface at the film's
 * own resolution; the subtitles' layer, when they are wanted and shown, is copied too and laid
 * over it. An HDR film is tone mapped to SDR by the system as it copies, or, asked to keep it on
 * Android 14 and later, copied with its PQ or HLG coding into a 16-bit PNG. One at a time: a key
 * held down does not queue pictures.
 */
internal class SnapshotController(private val activity: VideoPlayerActivity) {
    val state = SnapshotState()
    private val files = SnapshotFiles(activity.applicationContext)
    private var job: Job? = null

    fun take() {
        if (job?.isActive == true) return
        val settings = SnapshotSettings.read(Settings.getInstance(activity))
        // before Android 10 the default folder is a plain file: the storage permission, then again
        if (settings.folder == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            Permissions.askWriteStoragePermission(activity, false) { take() }
            return
        }
        job = activity.lifecycleScope.launch {
            state.saving = true
            state.outcome = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) capture(settings) else throw SnapshotException(SnapshotFailure.Unsupported)
            } catch (e: SnapshotException) {
                Log.w(TAG, "screenshot failed: ${e.reason}", e.cause)
                SnapshotOutcome.Failed(e.reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "screenshot failed", e)
                SnapshotOutcome.Failed(SnapshotFailure.Write)
            } finally {
                state.saving = false
            }
        }
    }

    /** Opens the screenshot in the gallery, or what else shows pictures. */
    fun open(saved: SnapshotOutcome.Saved) {
        val uri = saved.uri ?: return
        start(Intent(Intent.ACTION_VIEW).setDataAndType(uri, saved.format.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun share(saved: SnapshotOutcome.Saved) {
        val uri = saved.uri ?: return
        val send = Intent(Intent.ACTION_SEND).setType(saved.format.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(send, null))
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "nothing opens ${intent.action}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun capture(settings: SnapshotSettings): SnapshotOutcome {
        val service = activity.service ?: throw SnapshotException(SnapshotFailure.NoVideo)
        val layout = activity.videoLayout ?: throw SnapshotException(SnapshotFailure.NoVideo)
        val surface = layout.findViewById<SurfaceView>(R.id.surface_video)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }
        val texture = layout.findViewById<TextureView>(R.id.texture_video)?.takeIf { it.isShown && it.isAvailable }
        val view: View = surface ?: texture ?: throw SnapshotException(SnapshotFailure.NoVideo)

        val track = service.mediaplayer.getSelectedTrack(IMedia.Track.Type.Video) as? IMedia.VideoTrack
        val native = track?.takeIf { it.width > 0 && it.height > 0 }?.let {
            displaySize(it.width, it.height, it.sarNum, it.sarDen, turned = it.orientation >= IMedia.VideoTrack.Orientation.LeftTop)
        }
        val uri = service.currentMediaWrapper?.uri
        val title = service.title?.takeIf { it.isNotBlank() } ?: uri?.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
        val positionMs = service.getTime()
        val tech = uri?.let { runCatching { TechRepository.get(activity).cached(it.toString()) }.getOrNull() }
        val transfer = hdrTransferOf(tech?.video)
        val wantsHdr = transfer != null && settings.hdr == HdrSnapshot.Keep && surface != null

        // where the picture is: the whole view, or, when the player draws into all of it, its fitted part
        val viewRect = view.windowRect()
        val whole = view.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT
        val aspect = native?.let { it.width.toDouble() / it.height } ?: (view.width.toDouble() / view.height)
        val inView = if (whole) fitPicture(IntSize(view.width, view.height), aspect) else IntRect(0, 0, view.width, view.height)
        val picture = inView.translate(viewRect.topLeft)
        val size = limit(if (whole || native == null) IntSize(inView.width, inView.height) else native, wantsHdr)
        val source = if (whole) Rect(inView.left, inView.top, inView.right, inView.bottom) else null

        // the picture: kept HDR when asked and the system copies it so, else SDR
        val hdrFrame = if (wantsHdr && surface != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hdrBitmap(size, transfer!!)?.takeIf { copy(surface, source, it) }
        } else {
            null
        }
        val kept = hdrFrame != null
        val sdrFrame = when {
            kept -> null
            surface != null -> Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).takeIf { copy(surface, source, it) }
            // a texture is copied at its size on the screen
            else -> texture?.getBitmap(view.width, view.height)?.let { all ->
                if (whole) Bitmap.createBitmap(all, inView.left, inView.top, inView.width, inView.height) else all
            }
        }
        val frame: Bitmap = hdrFrame ?: sdrFrame ?: throw SnapshotException(SnapshotFailure.Capture)
        state.flashes++

        // the subtitles' layer, when they are wanted and some show
        val subtitlesView = layout.findViewById<SurfaceView>(R.id.surface_subtitles)
            ?.takeIf { settings.subtitles && surface != null && service.spuTrack != NO_TRACK && it.isShown && it.width > 0 && it.height > 0 }
        val (layer, drawn) = subtitlesView?.let { subtitleLayer(it) } ?: (null to null)
        val plan = snapshotLayout(picture, IntSize(frame.width, frame.height), subtitlesView?.windowRect(), drawn)

        // a small picture for the card; for an HDR frame, the system's SDR copy of it
        val thumbnail = if (kept) {
            thumbnailFromSurface(surface, source, plan)
        } else {
            withContext(Dispatchers.Default) { thumbnailOf(frame, layer, plan) }
        }

        val format = if (kept) SnapshotFormat.Png else settings.format
        val appName = activity.getString(R.string.nagmedar_shell_title)
        val name = snapshotFileName(title, positionMs, format, fallback = appName)
        val light = tech?.video?.let { ContentLight(it.maxCll, it.maxFall) }
        val written = withContext(Dispatchers.IO) {
            files.write(settings.folder, name, format.mime) { out ->
                if (kept && transfer != null) {
                    writeHdr(out, frame, layer, plan, transfer, light)
                } else {
                    val composed = compose(frame, layer, plan)
                    if (!composed.compress(compressFormat(format), 100, out)) throw SnapshotException(SnapshotFailure.Write)
                    if (composed !== frame) composed.recycle()
                }
            }
        }
        frame.recycle()
        layer?.recycle()
        return SnapshotOutcome.Saved(
            uri = written,
            fileName = name,
            folder = snapshotFolderLabel(settings.folder, defaultSnapshotFolder(appName)),
            format = format,
            hdr = when {
                transfer == null -> HdrOutcome.None
                kept -> HdrOutcome.Kept
                else -> HdrOutcome.ToneMapped
            },
            subtitles = layer != null,
            thumbnail = thumbnail?.asImageBitmap(),
            width = plan.width,
            height = plan.height,
        )
    }

    /** The subtitles' layer as it shows, and the part of it with something drawn; nothing when none is. */
    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun subtitleLayer(view: SurfaceView): Pair<Bitmap?, IntRect?> {
        val copied = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        if (!copy(view, null, copied)) return null to null
        val drawn = withContext(Dispatchers.Default) {
            val pixels = IntArray(copied.width * copied.height)
            copied.getPixels(pixels, 0, copied.width, 0, 0, copied.width, copied.height)
            drawnBounds(pixels, copied.width, copied.height)
        }
        return if (drawn != null) copied to drawn else null to null
    }

    /** An F16 bitmap coded as the film is (BT.2020 PQ or HLG): the system copies it without tone mapping. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun hdrBitmap(size: IntSize, transfer: HdrTransfer): Bitmap? = try {
        val space = ColorSpace.get(if (transfer == HdrTransfer.Pq) ColorSpace.Named.BT2020_PQ else ColorSpace.Named.BT2020_HLG)
        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGBA_F16, false, space)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "no HDR bitmap", e)
        null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun copy(view: SurfaceView, source: Rect?, into: Bitmap): Boolean = suspendCancellableCoroutine { done ->
        try {
            PixelCopy.request(view, source, into, { result -> if (done.isActive) done.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            // the surface went away
            done.resume(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private suspend fun thumbnailFromSurface(surface: SurfaceView?, source: Rect?, plan: SnapshotLayout): Bitmap? {
        surface ?: return null
        val size = thumbnailSize(plan.video.width, plan.video.height)
        val small = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        return small.takeIf { copy(surface, source, it) }
    }

    private companion object {
        const val TAG = "Nagmedar/Snapshot"
        const val NO_TRACK = "-1"

        /** At most 8K for 8-bit pictures; HDR ones (8 bytes a pixel) at most 4K's worth. */
        const val MAX_PIXELS = 7680 * 4320
        const val MAX_HDR_PIXELS = 4096 * 2304

        fun limit(size: IntSize, hdr: Boolean): IntSize {
            val max = if (hdr) MAX_HDR_PIXELS else MAX_PIXELS
            val pixels = size.width.toLong() * size.height
            if (pixels <= max) return size
            val scale = sqrt(max.toDouble() / pixels)
            return IntSize((size.width * scale).toInt().coerceAtLeast(1), (size.height * scale).toInt().coerceAtLeast(1))
        }

        fun View.windowRect(): IntRect {
            val at = IntArray(2)
            getLocationInWindow(at)
            return IntRect(at[0], at[1], at[0] + width, at[1] + height)
        }

        @Suppress("DEPRECATION")
        fun compressFormat(format: SnapshotFormat): Bitmap.CompressFormat = when {
            format == SnapshotFormat.Png -> Bitmap.CompressFormat.PNG
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSLESS
            // from Android 10, quality 100 is lossless
            else -> Bitmap.CompressFormat.WEBP
        }

        /** The picture with the subtitles over it, on black where the subtitles widen it. */
        fun compose(frame: Bitmap, layer: Bitmap?, plan: SnapshotLayout): Bitmap {
            if (layer == null && plan.width == frame.width && plan.height == frame.height) return frame
            val out = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(android.graphics.Color.BLACK)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                drawBitmap(frame, null, plan.video.toAndroidRect(), paint)
                if (layer != null && plan.subtitles != null) drawBitmap(layer, null, plan.subtitles.toAndroidRect(), paint)
            }
            return out
        }

        fun thumbnailSize(width: Int, height: Int): IntSize {
            val scale = THUMBNAIL_WIDTH.toDouble() / width.coerceAtLeast(1)
            return IntSize(THUMBNAIL_WIDTH, (height * scale).roundToInt().coerceAtLeast(1))
        }

        fun thumbnailOf(frame: Bitmap, layer: Bitmap?, plan: SnapshotLayout): Bitmap {
            val size = thumbnailSize(plan.width, plan.height)
            val small = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            val scaleX = size.width.toFloat() / plan.width
            val scaleY = size.height.toFloat() / plan.height
            Canvas(small).apply {
                drawColor(android.graphics.Color.BLACK)
                scale(scaleX, scaleY)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                drawBitmap(frame, null, plan.video.toAndroidRect(), paint)
                if (layer != null && plan.subtitles != null) drawBitmap(layer, null, plan.subtitles.toAndroidRect(), paint)
            }
            return small
        }

        const val THUMBNAIL_WIDTH = 320

        fun IntRect.toAndroidRect() = Rect(left, top, right, bottom)

        /**
         * The HDR picture as a 16-bit PNG: the frame's PQ or HLG codes as they are, the subtitles
         * at the reference white, blended over them, black where the subtitles widen it.
         */
        fun writeHdr(out: java.io.OutputStream, frame: Bitmap, layer: Bitmap?, plan: SnapshotLayout, transfer: HdrTransfer, light: ContentLight?) {
            val buffer = ByteBuffer.allocateDirect(frame.rowBytes * frame.height).order(ByteOrder.nativeOrder())
            frame.copyPixelsToBuffer(buffer)
            buffer.rewind()
            val halves = buffer.asShortBuffer()
            val stride = frame.rowBytes / 2
            val video = plan.video
            val subtitles = plan.subtitles
            val sub: IntArray? = if (layer != null && subtitles != null) {
                val scaled = Bitmap.createScaledBitmap(layer, subtitles.width.coerceAtLeast(1), subtitles.height.coerceAtLeast(1), true)
                IntArray(scaled.width * scaled.height).also { scaled.getPixels(it, 0, scaled.width, 0, 0, scaled.width, scaled.height) }
                    .also { if (scaled !== layer) scaled.recycle() }
            } else {
                null
            }
            val colours = SubtitleColours(transfer)
            HdrPng.write(out, plan.width, plan.height, transfer, light) { y, rgb ->
                rgb.fill(0)
                val vy = y - video.top
                if (vy in 0 until frame.height) {
                    val from = maxOf(0, video.left)
                    val to = minOf(plan.width, video.left + frame.width)
                    for (x in from until to) {
                        val i = vy * stride + (x - video.left) * 4
                        rgb[x * 3] = HalfToCode.code(halves.get(i))
                        rgb[x * 3 + 1] = HalfToCode.code(halves.get(i + 1))
                        rgb[x * 3 + 2] = HalfToCode.code(halves.get(i + 2))
                    }
                }
                if (sub != null && subtitles != null) {
                    val sy = y - subtitles.top
                    if (sy in 0 until subtitles.height) {
                        val from = maxOf(0, subtitles.left)
                        val to = minOf(plan.width, subtitles.left + subtitles.width)
                        for (x in from until to) {
                            val argb = sub[sy * subtitles.width + (x - subtitles.left)]
                            val alpha = argb ushr 24
                            if (alpha == 0) continue
                            val codes = colours.codes(argb)
                            rgb[x * 3] = blend((codes ushr 32).toInt() and 0xFFFF, rgb[x * 3], alpha)
                            rgb[x * 3 + 1] = blend((codes ushr 16).toInt() and 0xFFFF, rgb[x * 3 + 1], alpha)
                            rgb[x * 3 + 2] = blend(codes.toInt() and 0xFFFF, rgb[x * 3 + 2], alpha)
                        }
                    }
                }
            }
        }

        fun blend(over: Int, under: Short, alpha: Int): Short {
            val below = under.toInt() and 0xFFFF
            return ((over * alpha + below * (255 - alpha) + 127) / 255).toShort()
        }
    }
}
