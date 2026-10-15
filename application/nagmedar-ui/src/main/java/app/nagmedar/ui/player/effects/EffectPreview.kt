package app.nagmedar.ui.player.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The advanced effects drawn over a still frame (S-610), as libVLC does them: the side-by-side
 * halves mixed into an anaglyph, the mirror and quarter turns ([transform]), the free angle
 * within the picture's frame, then the logo and the text upright over it, sized as a share of the
 * picture as VLC sizes them in its pixels ([pictureHeight]). It is a picture of what the player
 * will show, not the player itself; the panel says so. Without a [frame], a sample picture.
 */
@Composable
fun EffectPreview(
    frame: ImageBitmap?,
    video: AdvancedVideo,
    transform: VideoTransform,
    logo: ImageBitmap?,
    pictureHeight: Int,
    nowMs: Long,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val sample = remember(density, direction) { samplePicture(density, direction) }
    val picture = frame ?: sample
    val measurer = rememberTextMeasurer()
    val text = video.text
    val line = remember(text.text, text.clock, nowMs) {
        val clock = text.clock.pattern?.let { SimpleDateFormat(it, Locale.ROOT).format(Date(nowMs)) }
        listOfNotNull(text.text.trim().takeIf { it.isNotEmpty() }, clock).joinToString(" ")
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black)
            .semantics { contentDescription = description }
            .testTag("effects-preview"),
    ) {
        val turned = transform.rotation % 180 != 0
        val aspect = picture.width.toFloat() / picture.height.coerceAtLeast(1)
        val shownAspect = if (turned) 1 / aspect else aspect
        val (w, h) = if (size.width / size.height > shownAspect) (size.height * shownAspect to size.height) else (size.width to size.width / shownAspect)
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        clipRect(left, top, left + w, top + h) {
            translate(left + w / 2, top + h / 2) {
                // the transform's turn first, its mirror next, the free angle last, as VLC's chain does them
                rotate(video.angle.toFloat(), pivot = Offset.Zero) {
                    scale(if (transform.flipHorizontal) -1f else 1f, if (transform.flipVertical) -1f else 1f, pivot = Offset.Zero) {
                        rotate(transform.rotation.toFloat(), pivot = Offset.Zero) {
                            val pw = if (turned) h else w
                            val ph = if (turned) w else h
                            drawSource(picture, video, Offset(-pw / 2, -ph / 2), Size(pw, ph))
                        }
                    }
                }
            }
        }
        val scale = h / pictureHeight.coerceAtLeast(1)
        val margin = h * AdvancedVideoOptions.MARGIN_SHARE
        val image = logo
        if (video.logo.enabled && image != null) {
            val lw = image.width * scale
            val lh = image.height * scale
            val (x, y) = AdvancedVideoOptions.place(video.logo.position, w, h, lw, lh, margin)
            drawImage(
                image,
                dstOffset = IntOffset((left + x).roundToInt(), (top + y).roundToInt()),
                dstSize = IntSize(lw.roundToInt().coerceAtLeast(1), lh.roundToInt().coerceAtLeast(1)),
                alpha = video.logo.opacityPercent / 100f,
                filterQuality = FilterQuality.Medium,
            )
        }
        if (text.enabled && line.isNotEmpty()) {
            val px = h * text.size.share
            val colour = Color(0xFF000000.toInt() or text.colour)
            val style = TextStyle(
                color = colour,
                fontSize = with(density) { px.toSp() },
                shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 1f), blurRadius = px / 10),
            )
            val layout = measurer.measure(line, style, maxLines = 1)
            val (x, y) = AdvancedVideoOptions.place(text.position, w, h, layout.size.width.toFloat(), layout.size.height.toFloat(), margin)
            drawText(layout, topLeft = Offset(left + x, top + y), alpha = text.opacityPercent / 100f)
        }
    }
}

/** The picture, or its two halves mixed as an anaglyph (each half stretched over the frame, as VLC's filter does). */
private fun DrawScope.drawSource(image: ImageBitmap, video: AdvancedVideo, topLeft: Offset, size: Size) {
    val offset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
    val dst = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
    if (!video.anaglyph) {
        drawImage(image, dstOffset = offset, dstSize = dst, filterQuality = FilterQuality.Medium)
        return
    }
    val (left, right) = video.anaglyphScheme.weights()
    val half = IntSize((image.width / 2).coerceAtLeast(1), image.height)
    drawImage(image, IntOffset.Zero, half, offset, dst, colorFilter = channels(left), filterQuality = FilterQuality.Medium)
    drawImage(image, IntOffset(image.width / 2, 0), half, offset, dst, colorFilter = channels(right), blendMode = BlendMode.Plus, filterQuality = FilterQuality.Medium)
}

private fun channels(weights: FloatArray) = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            weights[0], 0f, 0f, 0f, 0f,
            0f, weights[1], 0f, 0f, 0f,
            0f, 0f, weights[2], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** A picture to show the effects on when no frame could be taken: a sky over hills, the sun to the left. Content, not interface. */
private fun samplePicture(density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection): ImageBitmap {
    val image = ImageBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(density, direction, canvas, Size(SAMPLE_WIDTH.toFloat(), SAMPLE_HEIGHT.toFloat())) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B3A5C), Color(0xFF6FA8C9))))
        drawCircle(Color(0xFFF2C14E), radius = 22f, center = Offset(SAMPLE_WIDTH * 0.28f, SAMPLE_HEIGHT * 0.42f))
        drawOval(Color(0xFF2E5E3E), topLeft = Offset(-60f, SAMPLE_HEIGHT * 0.62f), size = Size(SAMPLE_WIDTH * 0.8f, SAMPLE_HEIGHT * 0.7f))
        drawOval(Color(0xFF3F7A4C), topLeft = Offset(SAMPLE_WIDTH * 0.35f, SAMPLE_HEIGHT * 0.68f), size = Size(SAMPLE_WIDTH * 0.9f, SAMPLE_HEIGHT * 0.6f))
    }
    return image
}

private const val SAMPLE_WIDTH = 320
private const val SAMPLE_HEIGHT = 180
