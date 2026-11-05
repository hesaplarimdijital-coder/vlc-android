package app.nagmedar.ui.input

import android.content.ClipData
import android.content.Context
import android.graphics.Point
import android.net.Uri
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagType
import java.io.File
import kotlin.math.roundToInt

/**
 * Tracks dragged out (S-406), as files other windows and apps take: the player's window, a second
 * Nağmedar window, a file manager. [files] gives what goes with the drag when it starts (the
 * selection, when the item dragged is in it) and [label] what its card says.
 *
 * 1.0.3 (Desktop Kit 2.3's K6, as in Samsung's My Files): a mouse (the keyboard cover's touchpad
 * is one) or a pen must first rest on the row for the system's touch and hold delay (Settings ›
 * Accessibility, half a second by default) and then move ([awaitHeldDrag]): a quick press and move
 * starts nothing and a slow click stays a click. The drag carries a [DragCard] with the label and,
 * for several files, their number in the accent colour. Touch keeps its drags for scrolling and its
 * long press for the menu.
 */
fun Modifier.fileDragSource(label: () -> String, files: () -> List<Uri>): Modifier = composed {
    val view = LocalView.current
    val context = LocalContext.current
    val look = rememberUpdatedState(
        CardLook(NagTheme.colors, NagTheme.type, LocalDensity.current, LocalFontFamilyResolver.current, LocalLayoutDirection.current),
    )
    val currentLabel = rememberUpdatedState(label)
    val currentFiles = rememberUpdatedState(files)
    pointerInput(view) {
        awaitEachGesture {
            awaitHeldDrag() ?: return@awaitEachGesture
            val uris = currentFiles.value().mapNotNull { shareable(context, it) }
            if (uris.isEmpty()) return@awaitEachGesture
            val text = currentLabel.value()
            val clip = ClipData.newUri(context.contentResolver, text, uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            val card = look.value.card(text, uris.size)
            view.startDragAndDrop(clip, DragCardShadow(card), NAGMEDAR_DRAG, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
        }
    }
}

/**
 * 1.0.3: the start of a drag out of a row. A mouse's main button (a touchpad's press is one) or a
 * pen goes down, rests within the touch slop for the system's touch and hold delay, then moves past
 * it: that move is returned, consumed, so the list does not scroll by it. Null for a finger, another
 * button, a press let go or moved away before the delay (a click, a quick drag) or let go after it
 * (a slow click), and a press whose moves something in the row took (a playlist row's handle).
 */
internal suspend fun AwaitPointerEventScope.awaitHeldDrag(): PointerInputChange? {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (down.type != PointerType.Mouse && down.type != PointerType.Stylus && down.type != PointerType.Eraser) return null
    val buttons = currentEvent.buttons
    if (down.type == PointerType.Mouse && !buttons.isPrimaryPressed) return null
    // a pen's button opens menus, as the mouse's second one does
    if (buttons.isSecondaryPressed) return null
    val slop = viewConfiguration.touchSlop
    // still for the whole delay: null; let go, moved away or taken before it ends: true
    val ledEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed || change.isConsumed || (change.position - down.position).getDistance() > slop) break
        }
        true
    }
    if (ledEarly != null) return null
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed || change.isConsumed) return null
        if ((change.position - down.position).getDistance() > slop) {
            change.consume()
            return change
        }
    }
}

/** A file's uri others may read: the app's FileProvider for a file, a content uri as it is; a stream is not a file. */
private fun shareable(context: Context, uri: Uri): Uri? = when (uri.scheme) {
    "content" -> uri
    "file" -> uri.path?.let { path ->
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.provider", File(path)) }.getOrNull()
    }
    else -> null
}

/** What a [DragCard] is drawn with: the window's colours, type and text settings when the drag starts. */
private class CardLook(
    val colors: NagColors,
    val type: NagType,
    val density: Density,
    val fonts: FontFamily.Resolver,
    val direction: LayoutDirection,
) {
    fun card(label: String, count: Int) = DragCard(label, count, colors, type, density, fonts, direction)
}

/** The system's drag shadow: the [card], with the pointer where the card wants it. */
internal class DragCardShadow(private val card: DragCard) : View.DragShadowBuilder() {
    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(card.width, card.height)
        outShadowTouchPoint.set(card.pointerX, card.pointerY)
    }

    override fun onDrawShadow(canvas: android.graphics.Canvas) = card.draw(canvas)
}

/**
 * 1.0.3 (Desktop Kit 2.3's K6): the card a drag out of the app carries, instead of a pill of the
 * inverse colours: [label] on the raised surface with an edge and a soft shadow, and for several
 * files their [count] ("99+" past 99) in an accent badge on its corner, as Samsung's My Files has
 * it. The pointer is under the card's middle, [GAP] below it, so the place it points at stays in
 * sight. [width] × [height] px is the picture, [pointerX], [pointerY] the pointer on it.
 */
internal class DragCard(
    label: String,
    count: Int,
    private val colors: NagColors,
    type: NagType,
    private val density: Density,
    fonts: FontFamily.Resolver,
    private val direction: LayoutDirection,
) {
    private val measurer = TextMeasurer(fonts, density, direction, cacheSize = 0)
    private val text = measurer.measure(
        AnnotatedString(label),
        style = type.bodyStrong.copy(color = colors.text),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = with(density) { LABEL_MAX.roundToPx() }),
    )

    /** The badge's number, or null for one file. */
    private val badge = if (count < 2) null else measurer.measure(
        AnnotatedString(if (count > 99) "99+" else count.toString()),
        style = type.labelSmall.copy(color = colors.onAccent, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )

    private val padding = with(density) { PADDING.toPx() }
    private val cardHeight = with(density) { maxOf(HEIGHT.toPx(), text.size.height + padding) }
    private val margin = with(density) { MARGIN.toPx() }
    private val gap = with(density) { GAP.toPx() }
    private val badgeHeight = with(density) { BADGE.toPx() }
    private val cardWidth = maxOf(with(density) { MIN_WIDTH.toPx() }, text.size.width + 2 * padding)

    /** Whether the card shows a count. */
    val counted: Boolean get() = badge != null

    val width: Int = (cardWidth + 2 * margin).roundToInt()
    val height: Int = (margin + cardHeight + gap).roundToInt()
    val pointerX: Int = width / 2
    val pointerY: Int = height - 1

    /** Where the card ends on the picture, px: [GAP] above the pointer. */
    val cardBottom: Float get() = margin + cardHeight

    fun DrawScope.drawCard() {
        val topLeft = Offset((size.width - cardWidth) / 2f, margin)
        val radius = with(density) { RADIUS.toPx() }
        val shadow = with(density) { 2.dp.toPx() }
        // a soft shadow: a few wider, fainter outlines under the card
        for (step in 3 downTo 1) {
            val grow = step * shadow
            drawRoundRect(
                Color.Black.copy(alpha = if (colors.isDark) 0.16f / step else 0.08f / step),
                topLeft = topLeft + Offset(-grow / 2, grow / 2),
                size = Size(cardWidth + grow, cardHeight + grow / 2),
                cornerRadius = CornerRadius(radius + grow / 2),
            )
        }
        val card = Size(cardWidth, cardHeight)
        drawRoundRect(colors.surfaceRaised, topLeft = topLeft, size = card, cornerRadius = CornerRadius(radius))
        drawRoundRect(colors.border, topLeft = topLeft, size = card, cornerRadius = CornerRadius(radius), style = Stroke(with(density) { 1.dp.toPx() }))
        drawText(text, topLeft = topLeft + Offset((cardWidth - text.size.width) / 2f, (cardHeight - text.size.height) / 2f))
        badge?.let { number ->
            val diameter = maxOf(badgeHeight, number.size.width + padding)
            // on the card's end corner, a quarter of it over the edge
            val x = if (direction == LayoutDirection.Ltr) cardWidth - diameter / 4 else diameter / 4
            val center = topLeft + Offset(x, diameter / 4)
            drawRoundRect(
                colors.accent,
                topLeft = center - Offset(diameter / 2, badgeHeight / 2),
                size = Size(diameter, badgeHeight),
                cornerRadius = CornerRadius(badgeHeight / 2),
            )
            drawText(number, topLeft = center - Offset(number.size.width / 2f, number.size.height / 2f))
        }
    }

    /** Draws the picture on [canvas] (the drag shadow's surface), from its top left corner. */
    fun draw(canvas: android.graphics.Canvas) {
        CanvasDrawScope().draw(density, direction, Canvas(canvas), Size(width.toFloat(), height.toFloat())) { drawCard() }
    }

    private companion object {
        val PADDING = 14.dp
        val HEIGHT = 40.dp
        val MIN_WIDTH = 96.dp
        val LABEL_MAX = 240.dp
        val RADIUS = 10.dp
        val MARGIN = 12.dp
        val GAP = 14.dp
        val BADGE = 22.dp
    }
}

/** The middle button on an item (S-408): in the library it adds the item to the queue. */
fun Modifier.onMiddleClick(action: () -> Unit): Modifier = composed {
    val current = rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed) {
                    event.changes.forEach { it.consume() }
                    current.value()
                }
            }
        }
    }
}
