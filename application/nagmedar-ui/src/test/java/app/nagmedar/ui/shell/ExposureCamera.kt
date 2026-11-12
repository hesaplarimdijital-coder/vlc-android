package app.nagmedar.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * One frame of the window, its pixels sorted by what they show when one screen is pure red, the other pure blue
 * and the theme dark (Desktop Kit 2.4.0's DetailNoDoubleExposureTest): a pixel with both red and blue in it is one
 * screen seen through the other ([mixed]); a blue one that is not pure blue is the blue screen part transparent
 * ([fadedBlue]); a red one with any green or blue in it is the red screen faded over the page ([fadedRed]; a
 * scrim is black, it only darkens the red: [redLevel]). A pure green mark tells where a screen is ([greenAt]),
 * and [blueRows] how tall the blue screen is drawn (1.0.4: whole, or shrunk by a Back gesture).
 */
internal class ExposureFrame(
    val label: String,
    val total: Int,
    val blue: Int,
    val red: Int,
    val mixed: Int,
    val fadedBlue: Int,
    val fadedRed: Int,
    private val redSum: Long,
    /** The pixels of the green mark that are drawn (under a scrim too); fewer than at rest when something covers a part of it. */
    val green: Int,
    private val greenXSum: Long,
    private val width: Int,
    /** The rows from the blue screen's topmost pixel to its lowest: its height as drawn; 0 when it is not. */
    val blueRows: Int = 0,
) {
    /** How bright the red screen's pixels are on average, 0 to 255: less than 255 under a scrim; 0 when there are none. */
    val redLevel: Int get() = if (red == 0) 0 else (redSum / red).toInt()

    /** Where the middle of what is drawn of the green mark is, as a share of the window's width; null when it is not drawn. */
    val greenAt: Float? get() = if (green < 16) null else greenXSum.toFloat() / green / width

    override fun toString() =
        "$label: blue $blue (faded $fadedBlue, $blueRows rows), red $red (faded $fadedRed, level $redLevel), mixed $mixed of $total, green $green at $greenAt"
}

/** Takes [ExposureFrame]s of the test activity's window. */
internal class ExposureCamera(private val compose: AndroidComposeTestRule<*, out ComponentActivity>) {
    private var buffer: Bitmap? = null
    private var pixels = IntArray(0)

    fun frame(label: String): ExposureFrame {
        val window = compose.activity.window.decorView
        val bitmap = buffer?.takeIf { it.width == window.width && it.height == window.height }
            ?: Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also {
                buffer = it
                pixels = IntArray(it.width * it.height)
            }
        compose.runOnIdle {
            bitmap.eraseColor(0)
            window.draw(Canvas(bitmap))
        }
        val width = bitmap.width
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)
        var blue = 0
        var red = 0
        var mixed = 0
        var fadedBlue = 0
        var fadedRed = 0
        var redSum = 0L
        var green = 0
        var greenXSum = 0L
        var blueFirst = -1
        var blueLast = -1
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = p shr 16 and 0xff
            val g = p shr 8 and 0xff
            val b = p and 0xff
            val reddish = r > 48
            val bluish = b > 48
            when {
                reddish && bluish -> mixed++
                bluish -> {
                    blue++
                    if (b < 250 || g > 2 || r > 2) fadedBlue++
                    if (blueFirst < 0) blueFirst = i
                    blueLast = i
                }
                reddish -> {
                    red++
                    redSum += r
                    if (g > 2 || b > 2) fadedRed++
                }
                g > 96 -> {
                    green++
                    greenXSum += i % width
                }
            }
        }
        val blueRows = if (blueFirst < 0) 0 else blueLast / width - blueFirst / width + 1
        return ExposureFrame(label, pixels.size, blue, red, mixed, fadedBlue, fadedRed, redSum, green, greenXSum, width, blueRows)
    }

    /** The next frame of the clock, then the window as it is. */
    fun step(label: String, frames: MutableList<ExposureFrame>) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        frames += frame(label)
    }
}
