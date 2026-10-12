package app.nagmedar.ui.player.snapshot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** A screenshot's size and where its two layers go in it, in its own pixels. */
@Immutable
data class SnapshotLayout(
    val width: Int,
    val height: Int,
    /** Where the film's picture goes. */
    val video: IntRect,
    /** Where the subtitles' layer goes, all of it (its empty parts are clear); null without subtitles. */
    val subtitles: IntRect?,
)

/**
 * Lays a screenshot out (S-606). [picture] is where the film's picture is on the screen and
 * [native] the size it is copied at (its own: a 4K film stays 4K in a small window). The
 * subtitles' layer covers [subtitlesView] on the screen; [subtitleContent] is the part of it that
 * has something drawn, in the layer's own pixels, or null. The picture's scale applies to the
 * subtitles too; subtitles drawn in the black bars beside the picture widen the screenshot to them,
 * on black.
 */
fun snapshotLayout(picture: IntRect, native: IntSize, subtitlesView: IntRect?, subtitleContent: IntRect?): SnapshotLayout {
    val scaleX = native.width.toDouble() / picture.width.coerceAtLeast(1)
    val scaleY = native.height.toDouble() / picture.height.coerceAtLeast(1)
    val drawn = if (subtitlesView != null && subtitleContent != null) subtitleContent.translate(subtitlesView.topLeft) else null
    val area = if (drawn != null) picture.union(drawn) else picture
    fun place(rect: IntRect) = IntRect(
        left = ((rect.left - area.left) * scaleX).roundToInt(),
        top = ((rect.top - area.top) * scaleY).roundToInt(),
        right = ((rect.right - area.left) * scaleX).roundToInt(),
        bottom = ((rect.bottom - area.top) * scaleY).roundToInt(),
    )
    val whole = place(area)
    return SnapshotLayout(
        width = whole.width.coerceAtLeast(1),
        height = whole.height.coerceAtLeast(1),
        video = place(picture),
        subtitles = if (drawn != null && subtitlesView != null) place(subtitlesView) else null,
    )
}

/** The smallest rectangle holding both. */
internal fun IntRect.union(other: IntRect) = IntRect(
    minOf(left, other.left),
    minOf(top, other.top),
    maxOf(right, other.right),
    maxOf(bottom, other.bottom),
)

/**
 * Where a film of [aspect] (width over height) shows in a [view] that the player draws it into
 * whole, as "best fit" places it: centred, as large as fits.
 */
fun fitPicture(view: IntSize, aspect: Double): IntRect {
    if (view.width <= 0 || view.height <= 0 || aspect <= 0.0) return IntRect(IntOffset.Zero, view)
    val viewAspect = view.width.toDouble() / view.height
    return if (aspect >= viewAspect) {
        val height = (view.width / aspect).roundToInt().coerceIn(1, view.height)
        val top = (view.height - height) / 2
        IntRect(0, top, view.width, top + height)
    } else {
        val width = (view.height * aspect).roundToInt().coerceIn(1, view.width)
        val left = (view.width - width) / 2
        IntRect(left, 0, left + width, view.height)
    }
}

/**
 * The part of a layer with something drawn in it: the bounds of its pixels that are not clear
 * ([pixels] as ARGB, row after row); null when it is all clear.
 */
fun drawnBounds(pixels: IntArray, width: Int, height: Int): IntRect? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        var first = -1
        var last = -1
        for (x in 0 until width) {
            if (pixels[row + x] ushr 24 != 0) {
                if (first < 0) first = x
                last = x
            }
        }
        if (first >= 0) {
            if (top == height) top = y
            bottom = y
            if (first < left) left = first
            if (last > right) right = last
        }
    }
    return if (right < 0) null else IntRect(left, top, right + 1, bottom + 1)
}

/**
 * The size a film's picture shows at: its pixels, made square by its sample aspect ratio
 * (anamorphic DVDs widen), turned with the picture when the file says it is (phones' videos).
 */
fun displaySize(width: Int, height: Int, sarNum: Int, sarDen: Int, turned: Boolean): IntSize {
    val square = if (sarNum > 0 && sarDen > 0 && sarNum != sarDen) (width.toLong() * sarNum / sarDen).toInt() else width
    return if (turned) IntSize(height, square) else IntSize(square, height)
}
