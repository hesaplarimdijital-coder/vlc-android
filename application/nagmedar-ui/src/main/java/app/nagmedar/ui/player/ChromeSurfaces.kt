package app.nagmedar.ui.player

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement

/**
 * Where the controls' own surfaces are in the player's window, in pixels: the panel beside or
 * under the video, the bars, the cards over it. The mouse's wheel over one of them is theirs, it
 * scrolls them; over the video alone it sets the volume or seeks
 * ([org.videolan.vlc.gui.video.PlayerChrome.isOverControls]). The sheets, the menus and the
 * dialogs are windows of their own, which the player's wheel never reaches. Read and written on
 * the main thread, as the window's events and the layout are.
 */
class ChromeSurfaces {
    private val areas = HashMap<Any, Rect>()
    private val views = ArrayList<View>()
    private val location = IntArray(2)

    /** [owner]'s surface is at [bounds] now. */
    fun place(owner: Any, bounds: Rect) {
        areas[owner] = bounds
    }

    /** [owner]'s surface went (its bars faded out, its card closed). */
    fun remove(owner: Any) {
        areas.remove(owner)
    }

    /** A view of its own that is a surface wherever it is, while it shows: the panel's. */
    fun addView(view: View) {
        if (view !in views) views += view
    }

    fun removeView(view: View) {
        views -= view
    }

    /** Whether the window's point ([x], [y]) is on one of the surfaces. */
    fun contains(x: Float, y: Float): Boolean {
        val point = Offset(x, y)
        return areas.values.any { it.contains(point) } || views.any { it.covers(x, y) }
    }

    private fun View.covers(x: Float, y: Float): Boolean {
        if (!isShown || width == 0 || height == 0) return false
        getLocationInWindow(location)
        return x >= location[0] && x < location[0] + width && y >= location[1] && y < location[1] + height
    }
}

/** This layout is a surface of the controls: its bounds in the window are [surfaces]' while it is laid out. */
fun Modifier.chromeSurface(surfaces: ChromeSurfaces): Modifier = this then ChromeSurfaceElement(surfaces)

private data class ChromeSurfaceElement(val surfaces: ChromeSurfaces) : ModifierNodeElement<ChromeSurfaceNode>() {
    override fun create() = ChromeSurfaceNode(surfaces)

    override fun update(node: ChromeSurfaceNode) {
        if (node.surfaces === surfaces) return
        node.surfaces.remove(node)
        node.surfaces = surfaces
    }
}

private class ChromeSurfaceNode(var surfaces: ChromeSurfaces) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        surfaces.place(this, coordinates.boundsInWindow())
    }

    override fun onDetach() {
        surfaces.remove(this)
    }
}

/**
 * The wheel over a surface of the controls ends there: what its scrolling parts leave (one at its
 * end, a part that does not scroll) goes no further. Taken after the parts inside have had it.
 */
fun Modifier.keepsWheel(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) event.changes.forEach { it.consume() }
        }
    }
}
