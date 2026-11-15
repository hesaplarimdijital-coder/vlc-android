package app.nagmedar.ui.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.covers.rememberCover
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 1.1.0 (Ebru): the inks of a band: up to five colours and the paper they float on. [seed] makes the same inks give the
 * same paper every time.
 */
@Immutable
data class EbruInks(val inks: List<Int>, val paper: Int, val seed: Long) {
    companion object {
        /** The band while nothing with a cover plays: the bozkır's reds, ochre, umber and teal on warm paper. */
        val Default = EbruInks(
            listOf(0xFFB8402B.toInt(), 0xFFD39A3E.toInt(), 0xFF4B2E22.toInt(), 0xFF2F6F6A.toInt(), 0xFFE9D7B5.toInt()),
            0xFFF0E3C8.toInt(),
            103L,
        )
    }
}

/**
 * 1.1.0 (Ebru): marbled paper, as the mockup's bands were made, with the mathematical marbling of Lu, Jaffer et al.:
 * ink drops spread on the tray, each pushing the outlines before it outwards (an area-preserving map), then a stylus is
 * drawn back and forth through them, a fine comb and a slow wave; the bottom edge is torn as handmade paper is. The
 * same [EbruInks] give the same band.
 */
internal object Ebru {

    /** A band [width] × [height] pixels; transparent under its torn edge. */
    fun band(inks: EbruInks, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(16).toFloat()
        val h = height.coerceAtLeast(16).toFloat()
        val random = Random(inks.seed)
        val tray = Tray(maxSegment = max(1.6f, h / 90f), cap = 2400)
        val colours = inks.inks.ifEmpty { EbruInks.Default.inks }
        // drops: as many as the mockup's 1800 × 520 band had for its shape, radii from its height
        val count = (120f * (w / h) / (1800f / 520f)).roundToInt().coerceIn(48, 150)
        val margin = 0.12f
        for (i in 0 until count) {
            val x = (random.nextFloat() * (1 + 2 * margin) - margin) * w
            val y = (random.nextFloat() * (1 + 2 * margin) - margin) * h
            val r = (0.05f + random.nextFloat() * 0.07f) * h
            tray.drop(x, y, r, colours[i % colours.size])
        }
        // gelgit: the stylus back and forth across the tray, then across the other way
        var k = 0
        var x = w / 14f * 0.5f
        while (x < w + w / 14f) {
            tray.tine(x, 0f, 0f, if (k % 2 == 0) 1f else -1f, h * 0.30f, w * 0.008f)
            x += w / 14f
            k++
        }
        k = 0
        var y = h / 6f * 0.5f
        while (y < h + h / 6f) {
            tray.tine(0f, y, if (k % 2 == 0) 1f else -1f, 0f, w * 0.05f, h * 0.01f)
            y += h / 6f
            k++
        }
        // a fine comb, then a slow wave along the band
        y = h / 30f / 2f
        while (y < h + h / 30f) {
            tray.tine(0f, y, 1f, 0f, w * 0.012f, h * 0.003f)
            y += h / 30f
        }
        tray.wave(h * 0.05f, w * 0.18f, 1.2f)
        return render(tray, inks.paper, width.coerceAtLeast(16), height.coerceAtLeast(16), random)
    }

    private fun render(tray: Tray, paper: Int, width: Int, height: Int, random: Random): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(paper)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (d in tray.drops) {
            path.rewind()
            path.moveTo(d.xs[0], d.ys[0])
            for (i in 1 until d.n) path.lineTo(d.xs[i], d.ys[i])
            path.close()
            paint.color = d.colour
            canvas.drawPath(path, paint)
        }
        tear(canvas, paper, width.toFloat(), height.toFloat(), random)
        return bitmap
    }

    /** The torn bottom edge: under it the page shows through, along it a pale line of the paper's fibre. */
    private fun tear(canvas: Canvas, paper: Int, w: Float, h: Float, random: Random) {
        val depth = 0.11f
        val steps = w.roundToInt().coerceAtLeast(2)
        val edge = FloatArray(steps + 1)
        val amplitudes = FloatArray(6) { (random.nextGaussian() / (it + 1)).toFloat() }
        val frequencies = FloatArray(6) { 1.5f + random.nextFloat() * 3f }
        val phases = FloatArray(6) { random.nextFloat() * 6.3f }
        var peak = 0f
        for (i in 0..steps) {
            var e = 0f
            for (n in 0 until 6) e += amplitudes[n] * sin(2f * PI.toFloat() * i * (n + 1) / steps * frequencies[n] + phases[n])
            edge[i] = e
            peak = max(peak, abs(e))
        }
        // a little roughness on the long waves
        var rough = 0f
        val base = h * (1 - depth * 0.62f)
        for (i in 0..steps) {
            rough = rough * 0.8f + (random.nextFloat() - 0.5f) * 0.4f
            edge[i] = base + edge[i] / peak.coerceAtLeast(1e-3f) * h * depth * 0.45f + rough * h * 0.012f
        }
        val below = Path().apply {
            moveTo(0f, edge[0])
            for (i in 1..steps) lineTo(i * w / steps, edge[i])
            lineTo(w, h + 1)
            lineTo(0f, h + 1)
            close()
        }
        canvas.drawPath(below, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        val line = Path().apply {
            moveTo(0f, edge[0] - 1.2f)
            for (i in 1..steps) lineTo(i * w / steps, edge[i] - 1.2f)
        }
        val fibre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (h / 70f).coerceIn(1.5f, 3f)
            color = paper
            alpha = 190
        }
        canvas.drawPath(line, fibre)
    }

    /** One ink's outline on the tray. */
    private class Drop(var xs: FloatArray, var ys: FloatArray, var n: Int, val colour: Int)

    /** The tray and what floats on it; [maxSegment] keeps the outlines smooth, [cap] their size. */
    private class Tray(private val maxSegment: Float, private val cap: Int) {
        val drops = ArrayList<Drop>()
        private val limit = maxSegment * maxSegment

        fun drop(cx: Float, cy: Float, r: Float, colour: Int) {
            val r2 = r * r
            for (d in drops) {
                map(d) { x, y, out ->
                    val vx = x - cx
                    val vy = y - cy
                    val s = sqrt(1f + r2 / (vx * vx + vy * vy + 1e-6f))
                    out[0] = cx + vx * s
                    out[1] = cy + vy * s
                }
            }
            val n = (2 * PI * r / maxSegment).toInt().coerceIn(24, 360)
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            for (i in 0 until n) {
                val t = 2 * PI * i / n
                xs[i] = cx + r * cos(t).toFloat()
                ys[i] = cy + r * sin(t).toFloat()
            }
            drops += Drop(xs, ys, n, colour)
        }

        /** The stylus drawn through [bx], [by] in the direction ([mx], [my]): [alpha] at the line, less with distance ([lambda]). */
        fun tine(bx: Float, by: Float, mx: Float, my: Float, alpha: Float, lambda: Float) {
            val nx = -my
            val ny = mx
            for (d in drops) {
                map(d) { x, y, out ->
                    val dist = abs((x - bx) * nx + (y - by) * ny)
                    val shift = alpha * lambda / (dist + lambda)
                    out[0] = x + shift * mx
                    out[1] = y + shift * my
                }
            }
        }

        /** Every outline moved sideways by a sine of its height on the band. */
        fun wave(amplitude: Float, length: Float, phase: Float) {
            for (d in drops) {
                map(d) { x, y, out ->
                    out[0] = x + amplitude * sin(2f * PI.toFloat() * y / length + phase)
                    out[1] = y
                }
            }
        }

        /** [d]'s outline through [f], segments grown longer than [maxSegment] split at their mapped middles. */
        private inline fun map(d: Drop, f: (Float, Float, FloatArray) -> Unit) {
            val out = FloatArray(2)
            var px = d.xs
            var py = d.ys
            var n = d.n
            var qx = FloatArray(n)
            var qy = FloatArray(n)
            for (i in 0 until n) {
                f(px[i], py[i], out)
                qx[i] = out[0]
                qy[i] = out[1]
            }
            for (round in 0 until 4) {
                if (n >= cap) break
                var long = 0
                for (i in 0 until n) {
                    val j = if (i + 1 == n) 0 else i + 1
                    val dx = qx[j] - qx[i]
                    val dy = qy[j] - qy[i]
                    if (dx * dx + dy * dy > limit) long++
                }
                if (long == 0) break
                val size = min(n + long, cap)
                val npx = FloatArray(size)
                val npy = FloatArray(size)
                val nqx = FloatArray(size)
                val nqy = FloatArray(size)
                var m = 0
                for (i in 0 until n) {
                    if (m >= size) break
                    npx[m] = px[i]
                    npy[m] = py[i]
                    nqx[m] = qx[i]
                    nqy[m] = qy[i]
                    m++
                    val j = if (i + 1 == n) 0 else i + 1
                    val dx = qx[j] - qx[i]
                    val dy = qy[j] - qy[i]
                    if (dx * dx + dy * dy > limit && m < size) {
                        val mx = (px[i] + px[j]) / 2
                        val my = (py[i] + py[j]) / 2
                        f(mx, my, out)
                        npx[m] = mx
                        npy[m] = my
                        nqx[m] = out[0]
                        nqy[m] = out[1]
                        m++
                    }
                }
                px = npx
                py = npy
                qx = nqx
                qy = nqy
                n = m
            }
            d.xs = qx
            d.ys = qy
            d.n = n
        }
    }

    /**
     * The inks of a cover: its colours by how much of it they cover (a small k-means over a sample of its pixels), the
     * palest one, when pale enough, the paper (warmed a little toward handmade paper), else a warm paper.
     */
    fun inksOf(pixels: IntArray, seed: Long): EbruInks? {
        val samples = pixels.filter { it ushr 24 >= 128 }
        if (samples.size < 16) return null
        val k = 6
        val random = Random(seed)
        val cr = FloatArray(k)
        val cg = FloatArray(k)
        val cb = FloatArray(k)
        for (c in 0 until k) {
            val p = samples[random.nextInt(samples.size)]
            cr[c] = (p shr 16 and 0xFF).toFloat()
            cg[c] = (p shr 8 and 0xFF).toFloat()
            cb[c] = (p and 0xFF).toFloat()
        }
        val counts = IntArray(k)
        repeat(8) {
            val sr = FloatArray(k)
            val sg = FloatArray(k)
            val sb = FloatArray(k)
            counts.fill(0)
            for (p in samples) {
                val r = (p shr 16 and 0xFF).toFloat()
                val g = (p shr 8 and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                var best = 0
                var bestD = Float.MAX_VALUE
                for (c in 0 until k) {
                    val dr = r - cr[c]
                    val dg = g - cg[c]
                    val db = b - cb[c]
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestD) {
                        bestD = dist
                        best = c
                    }
                }
                sr[best] += r
                sg[best] += g
                sb[best] += b
                counts[best]++
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    cr[c] = sr[c] / counts[c]
                    cg[c] = sg[c] / counts[c]
                    cb[c] = sb[c] / counts[c]
                }
            }
        }
        val clusters = (0 until k).filter { counts[it] > 0 }.sortedByDescending { counts[it] }
        fun argb(c: Int) = (0xFF shl 24) or (cr[c].roundToInt().coerceIn(0, 255) shl 16) or (cg[c].roundToInt().coerceIn(0, 255) shl 8) or
            cb[c].roundToInt().coerceIn(0, 255)
        fun light(c: Int) = (0.2126f * cr[c] + 0.7152f * cg[c] + 0.0722f * cb[c]) / 255f
        val palest = clusters.maxByOrNull { light(it) } ?: return null
        val paper = if (light(palest) > 0.72f) mixArgb(argb(palest), WARM_PAPER, 0.55f) else WARM_PAPER
        // distinct inks: one a little away from the last kept
        val inks = mutableListOf<Int>()
        for (c in clusters) {
            if (c == palest && light(palest) > 0.72f) continue
            val colour = argb(c)
            if (inks.none { near(it, colour) }) inks += colour
        }
        if (inks.size < 2) return null
        return EbruInks(inks.take(5), paper, seed)
    }

    private fun near(a: Int, b: Int): Boolean {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db < 24 * 24
    }

    private fun mixArgb(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * f + (b shr shift and 0xFF) * (1 - f)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Handmade paper's warm white. */
    private const val WARM_PAPER = 0xFFF0E6D2.toInt()

    /** The bands made, by their inks and size, up to 6 MB: a page that comes back shows its band at once. */
    private val made = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(inks: EbruInks, width: Int, height: Int): Bitmap? = made.get(key(inks, width, height))

    fun make(inks: EbruInks, width: Int, height: Int): Bitmap =
        cached(inks, width, height) ?: band(inks, width, height).also { made.put(key(inks, width, height), it) }

    private fun key(inks: EbruInks, width: Int, height: Int) = "${inks.inks.joinToString(",")}|${inks.paper}|${inks.seed}|$width×$height"

    /** Robolectric's screenshots: the band is drawn at once, so the picture never catches it on its way. */
    val synchronous: Boolean = Build.FINGERPRINT == "robolectric"
}

/**
 * 1.1.0 (Ebru): the inks of [media]'s cover (its album's), read off the main thread and kept for it; [EbruInks.Default]
 * while nothing with a cover plays.
 */
@Composable
internal fun rememberEbruInks(media: MediaWrapper?): EbruInks {
    val track = remember(media?.id, media?.uri) { media?.let { CoverTrack.of(it) } }
    val cover = rememberCover(track, COVER_SAMPLE_PX)
    val inks by produceState(EbruInks.Default, cover, track?.key) {
        val image = cover ?: run {
            value = EbruInks.Default
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val source = image.asAndroidBitmap()
                val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
                val small = Bitmap.createScaledBitmap(readable, 32, 32, true)
                val pixels = IntArray(32 * 32)
                small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                Ebru.inksOf(pixels, (track?.key ?: "").hashCode().toLong())
            }.getOrNull() ?: EbruInks.Default
        }
    }
    return inks
}

/**
 * 1.1.0 (Ebru): a band of marbled paper across the page's top, [height] high, made from [inks] off the main thread at
 * most [MAX_BAND_PX] pixels wide and stretched to the width; a new album's band fades in over the last one. Decoration
 * only: TalkBack passes over it.
 */
@Composable
fun EbruBand(inks: EbruInks, height: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        val density = LocalDensity.current
        val fullWidth = constraints.maxWidth.coerceAtLeast(1)
        val fullHeight = with(density) { height.roundToPx() }.coerceAtLeast(1)
        val scale = min(1f, MAX_BAND_PX.toFloat() / fullWidth)
        val w = (fullWidth * scale).roundToInt().coerceAtLeast(16)
        val h = (fullHeight * scale).roundToInt().coerceAtLeast(16)
        // the band on screen stays until the next one is made, then fades into it (an album that plays after another)
        val image = remember { mutableStateOf((Ebru.cached(inks, w, h) ?: if (Ebru.synchronous) Ebru.make(inks, w, h) else null)?.asImageBitmap()) }
        LaunchedEffect(inks, w, h) {
            val ready = Ebru.cached(inks, w, h) ?: withContext(Dispatchers.Default) { Ebru.make(inks, w, h) }
            if (image.value?.asAndroidBitmap() !== ready) image.value = ready.asImageBitmap()
        }
        val instant = NagTheme.motion.reduced
        Crossfade(image.value, animationSpec = tween(if (instant) 0 else NagMotion.LONG), label = "ebruBand") { shown ->
            if (shown != null) Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

/** The cover is read this large for its inks. */
private const val COVER_SAMPLE_PX = 96

/** A band is made at most this many pixels wide: a phone's and DeX's full width, a large tablet's a little stretched. */
private const val MAX_BAND_PX = 1600
