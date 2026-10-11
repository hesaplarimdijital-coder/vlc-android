package app.nagmedar.ui.input

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.text.TextPaint
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import app.nagmedar.ui.theme.NagTheme
import java.io.File

/**
 * Films and tracks dragged out with the mouse (S-406), as files other windows and apps take: the
 * player's window, a second Nağmedar window, a file manager. [files] gives what goes with the drag
 * when it starts (the selection, when the item dragged is in it) and [label] what the shadow says.
 * Touch keeps its drags for scrolling and its long press for the menu.
 */
fun Modifier.fileDragSource(label: () -> String, files: () -> List<Uri>): Modifier = composed {
    val view = LocalView.current
    val context = LocalContext.current
    val fill = NagTheme.colors.inverseSurface.toArgb()
    val text = NagTheme.colors.onInverseSurface.toArgb()
    val currentLabel = rememberUpdatedState(label)
    val currentFiles = rememberUpdatedState(files)
    pointerInput(view) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
            // the system's drag and drop between windows came with Android 7
            if (!moved || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return@awaitEachGesture
            val uris = currentFiles.value().mapNotNull { shareable(context, it) }
            if (uris.isEmpty()) return@awaitEachGesture
            val clip = ClipData.newUri(context.contentResolver, currentLabel.value(), uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            val shadow = LabelShadow(currentLabel.value(), context.resources.displayMetrics.density, fill, text)
            view.startDragAndDrop(clip, shadow, NAGMEDAR_DRAG, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
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

/** What the drag shows under the pointer: a pill with the title, or how many go. */
private class LabelShadow(text: String, private val density: Float, fill: Int, ink: Int) : View.DragShadowBuilder() {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 14 * density
    }
    private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val padding = 12 * density
    private val shown = TextUtils.ellipsize(text, paint, 280 * density, TextUtils.TruncateAt.END).toString()
    private val width = (paint.measureText(shown) + 2 * padding).toInt().coerceAtLeast(1)
    private val height = (34 * density).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set((8 * density).toInt(), height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = 8 * density
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, back)
        canvas.drawText(shown, padding, height / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    }
}
