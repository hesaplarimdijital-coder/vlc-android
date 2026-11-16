package app.nagmedar.ui.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.screenshots.ShellScreenshotTest
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * 1.1.3: one album's band made before the eyes over the last one ([MarblingShader]), drawn by its shader: Robolectric's
 * Skia compiles it and runs it on the processor. It starts on the band there was, ends on the new one exactly as the band
 * is made, and on the way is neither; making a band's drops for it changes nothing of the band.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EbruMarblingTest {

    private val night = EbruInks(
        listOf(0xFF1F2A5A.toInt(), 0xFFE7A33E.toInt(), 0xFF2E9C9A.toInt(), 0xFFB4412F.toInt(), 0xFF121833.toInt()),
        0xFFEFE3CC.toInt(),
        101L,
    )

    private val page = 0xFF15161A.toInt()

    /** The shader as Robolectric's older Skia reads it: it samples a child with sample(child, p), not child.eval(p). */
    private fun olderSource() = MarblingShader.SOURCE.replace(Regex("""(\w+)\.eval\(""")) { "sample(" + it.groupValues[1] + ", " }

    private fun frame(shader: RuntimeShader, t: Float, w: Int, h: Int): Bitmap {
        MarblingShader.frame(shader, t, 1f, 1f)
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        // a software canvas refuses a runtime shader unless told (Skia's raster pipeline draws it)
        canvas.javaClass.getMethod("setHwFeaturesInSwModeEnabled", Boolean::class.javaPrimitiveType).invoke(canvas, true)
        canvas.drawColor(page)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
        return frame
    }

    /** [band] over the page, as it shows. */
    private fun shown(band: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(band.width, band.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(page)
            drawBitmap(band, 0f, 0f, null)
        }
        return out
    }

    /** How many of the pixels differ by more than [tolerance] in a channel. */
    private fun differing(a: Bitmap, b: Bitmap, tolerance: Int): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            val most = maxOf(abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)), abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)), abs((p and 0xFF) - (q and 0xFF)))
            if (most > tolerance) count++
        }
        return count
    }

    @Test
    fun startsOnTheOldBandAndEndsOnTheNew() {
        val w = 540
        val h = 210
        val old = Ebru.band(EbruInks.Default, w, h)
        val made = Ebru.made(night, w, h, stones = true)
        val shader = MarblingShader.make(old, EbruInks.Default.paper, made.band!!, made.stones!!, made.plan, olderSource())
        // 1.1.5: the drops, the stylus down and up, across once (to 0.9), the new band
        val times = listOf(0f, 0.12f, 0.3f, 0.45f, 0.62f, 0.78f, 1f)
        val frames = times.map { frame(shader, it, w, h) }
        // the first frame is the band there was (its edges' anti-aliasing aside), the last the new one
        assertTrue(differing(frames.first(), shown(old), 16) < w * h / 1000)
        assertEquals(0, differing(frames.last(), shown(made.band!!), 2))
        // on the way it is neither: the drops falling, the stylus' lines
        for (middle in frames.subList(1, frames.size - 1)) {
            assertTrue(differing(middle, shown(old), 16) > w * h / 10)
            assertTrue(differing(middle, shown(made.band!!), 16) > w * h / 20)
        }
        // the way, for approval: from the bozkır's band to a night sky's
        val strip = Bitmap.createBitmap(w, (h + 8) * frames.size, Bitmap.Config.ARGB_8888)
        Canvas(strip).apply {
            drawColor(page)
            frames.forEachIndexed { i, f -> drawBitmap(f, 0f, (i * (h + 8)).toFloat(), null) }
        }
        ShellScreenshotTest.save("ebru-marbling", strip)
    }

    @Test
    fun makingTheDropsChangesNothingOfTheBand() {
        val w = 400
        val h = 160
        val band = Ebru.band(night, w, h)
        val made = Ebru.made(night, w, h, stones = true)
        assertEquals(0, differing(band, made.band!!, 0))
        // the drops of a kept band, made again for its transition, are the same as with it
        val again = Ebru.made(night, w, h, stones = true, stylus = false)
        assertEquals(null, again.band)
        assertEquals(0, differing(made.stones!!, again.stones!!, 0))
        assertEquals(made.plan.count, again.plan.count)
        assertTrue(made.plan.drops.contentEquals(again.plan.drops))
        // the drops lie around the band too, as far as the stylus pulls
        val (mx, my) = Ebru.stonesMargin(w, h)
        assertEquals(w + 2 * mx, made.stones!!.width)
        assertEquals(h + 2 * my, made.stones!!.height)
    }

    @Test
    fun theBandsStepsAreTheShadersSteps() {
        val plan = Ebru.made(night, 1079, 420).plan
        // the stylus down and up 15 times, across 7 (1.1.5: once across, no comb after it): within what the shader undoes
        assertEquals(15, plan.vertical.count)
        assertEquals(7, plan.horizontal.count)
        assertTrue(plan.count in 48..MAX_DROPS)
        assertTrue(plan.vertical.alternate && plan.horizontal.alternate)
    }
}
