package app.nagmedar.ui.music

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.covers.CoverCenter
import app.nagmedar.ui.covers.CoverTrack
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.contrast
import app.nagmedar.ui.theme.mixSrgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.util.ThumbnailsProvider
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * S-1002: the colour an artwork reads as, for the phone player's backdrop: the hue that most of its lively pixels share
 * (with the two beside it on the colour wheel), as the average of those pixels. [pixels] are ARGB; greys, near-black
 * and near-white pixels do not count. Null for a picture of greys, or one whose colour covers too little of it.
 */
internal fun dominantColor(pixels: IntArray): Int? {
    val weight = FloatArray(HUES)
    val red = FloatArray(HUES)
    val green = FloatArray(HUES)
    val blue = FloatArray(HUES)
    val count = IntArray(HUES)
    var opaque = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < OPAQUE) continue
        opaque++
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val chroma = max - min
        val light = (max + min) / 2
        if (chroma < MIN_CHROMA || light < MIN_LIGHT || light > MAX_LIGHT) continue
        val saturation = chroma / (1f - abs(2f * light - 1f))
        val hue = when (max) {
            r -> ((g - b) / chroma).mod(6f)
            g -> (b - r) / chroma + 2f
            else -> (r - g) / chroma + 4f
        } / 6f
        val bin = (hue * HUES).toInt().coerceIn(0, HUES - 1)
        // the lively, neither dark nor pale, count most
        val w = saturation * (1f - abs(2f * light - 1f) / 2f)
        weight[bin] += w
        red[bin] += r * w
        green[bin] += g * w
        blue[bin] += b * w
        count[bin]++
    }
    if (opaque == 0) return null
    fun around(values: FloatArray, bin: Int) = values[(bin + HUES - 1) % HUES] + values[bin] + values[(bin + 1) % HUES]
    val best = (0 until HUES).maxByOrNull { around(weight, it) } ?: return null
    val total = around(weight, best)
    val pixelsOfIt = count[(best + HUES - 1) % HUES] + count[best] + count[(best + 1) % HUES]
    if (total <= 0f || pixelsOfIt < opaque * MIN_SHARE) return null
    fun channel(values: FloatArray) = (around(values, best) / total * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

/** Hue bins of the colour wheel, 15° each. */
private const val HUES = 24

/** Alpha below which a pixel is not looked at. */
private const val OPAQUE = 128

/** A pixel this close to grey, or this dark or pale, is not a colour. */
private const val MIN_CHROMA = 0.12f
private const val MIN_LIGHT = 0.08f
private const val MAX_LIGHT = 0.92f

/** The colour has to cover this much of the picture. */
private const val MIN_SHARE = 0.03f

/**
 * S-1002: the top of the phone player's backdrop in [colors]: the artwork's [color] as a deep tone on the dark themes
 * and a pale one on the light theme, mixed into the background no further than keeps the text, the secondary and the
 * tertiary text at 4.5:1 over it. The background itself when no mix does.
 */
internal fun backdropTop(colors: NagColors, color: Color): Color {
    val (hue, saturation, _) = hsl(color)
    val tone = hslColor(
        hue,
        saturation.coerceAtMost(if (colors.isDark) DARK_SATURATION else LIGHT_SATURATION),
        if (colors.isDark) DARK_LIGHTNESS else LIGHT_LIGHTNESS,
    )
    var strength = if (colors.isDark) DARK_STRENGTH else LIGHT_STRENGTH
    while (strength > 0f) {
        val top = mixSrgb(tone, colors.background, strength)
        if (readable(colors, top)) return top
        strength -= STRENGTH_STEP
    }
    return colors.background
}

private fun readable(colors: NagColors, background: Color): Boolean =
    listOf(colors.text, colors.textSecondary, colors.textTertiary).all { contrast(it, background) >= MIN_CONTRAST }

private const val DARK_SATURATION = 0.55f
private const val DARK_LIGHTNESS = 0.3f
private const val DARK_STRENGTH = 0.8f
private const val LIGHT_SATURATION = 0.5f
private const val LIGHT_LIGHTNESS = 0.82f
private const val LIGHT_STRENGTH = 0.9f
private const val STRENGTH_STEP = 0.05f
private const val MIN_CONTRAST = 4.5

/** Hue (0–1), saturation and lightness of an sRGB colour. */
private fun hsl(color: Color): Triple<Float, Float, Float> {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val light = (max + min) / 2
    val chroma = max - min
    if (chroma == 0f) return Triple(0f, 0f, light)
    val saturation = chroma / (1f - abs(2f * light - 1f))
    val hue = when (max) {
        color.red -> ((color.green - color.blue) / chroma).mod(6f)
        color.green -> (color.blue - color.red) / chroma + 2f
        else -> (color.red - color.green) / chroma + 4f
    } / 6f
    return Triple(hue, saturation.coerceIn(0f, 1f), light)
}

private fun hslColor(hue: Float, saturation: Float, light: Float): Color {
    val chroma = (1f - abs(2f * light - 1f)) * saturation
    val h = hue * 6f
    val x = chroma * (1f - abs(h.mod(2f) - 1f))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = light - chroma / 2
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

/**
 * S-1002: the colour of [media]'s artwork (its album's cover, else its thumbnail; [dominantColor]), read off the main
 * thread and again when a cover changes; null without artwork or colour.
 */
@Composable
internal fun rememberArtworkColor(media: MediaWrapper?): Color? {
    val context = LocalContext.current
    val changes by CoverCenter.changes.collectAsState()
    val color by produceState<Color?>(null, media?.id, media?.artworkMrl, media?.uri, changes) {
        value = media?.let { item ->
            val track = if (item.type == MediaWrapper.TYPE_AUDIO) CoverTrack.of(item) else null
            val image = track?.let { orNull { CoverCenter.image(context, it, SAMPLE_PX) } }
                ?: orNull { ThumbnailsProvider.obtainBitmap(item, SAMPLE_PX)?.asImageBitmap() }
            image?.let { picture -> withContext(Dispatchers.Default) { orNull { sampled(picture) } } }?.let { argb -> Color(argb) }
        }
    }
    return color
}

/** [block]'s result, null when it fails (a file that does not decode); a cancellation goes on. */
private inline fun <T> orNull(block: () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** The dominant colour of [image], looked for in a small copy of it. */
private fun sampled(image: ImageBitmap): Int? {
    val source = image.asAndroidBitmap()
    // a hardware bitmap's pixels cannot be read
    val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE
    val readable = if (hardware) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val small = Bitmap.createScaledBitmap(readable, SAMPLE, SAMPLE, true)
    val pixels = IntArray(SAMPLE * SAMPLE)
    small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
    if (small !== readable) small.recycle()
    if (readable !== source) readable.recycle()
    return dominantColor(pixels)
}

/** The cover is read this large, and looked at this small. */
private const val SAMPLE_PX = 96
private const val SAMPLE = 32
