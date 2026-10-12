package app.nagmedar.ui.player.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the panel's view shows: its dock and, under the video, how many columns its sections take. */
class PanelLayout(val dock: PanelDock, val columns: Int)

/**
 * Docks the player's panel beside or under the classic player (S-603). The player's root view
 * makes room for it with a margin, so the video and the classic overlay shrink next to it rather
 * than under it; the panel is its own view in the window's content, so a finger reaches it as a
 * mouse does. The window's size decides the side ([PanelDock.of]) and follows a resized window.
 */
internal class PanelHost(
    private val owner: LifecycleOwner,
    private val playerRoot: View,
    private val state: PanelState,
    private val content: @Composable (PanelLayout) -> Unit,
) {
    private var holder: PanelFrame? = null
    private var following: Job? = null
    private var layout by mutableStateOf(PanelLayout(PanelDock.Side, 1))
    private val resized = View.OnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) view.post { relayout() }
    }

    fun attach() {
        val window = playerRoot.parent as? FrameLayout ?: return
        val frame = PanelFrame(playerRoot.context).apply { visibility = View.GONE }
        frame.addView(
            ComposeView(playerRoot.context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent { content(layout) }
            },
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        window.addView(frame, FrameLayout.LayoutParams(0, 0))
        window.addOnLayoutChangeListener(resized)
        holder = frame
        following = owner.lifecycleScope.launch {
            snapshotFlow { state.shown }.collect { relayout() }
        }
    }

    fun detach() {
        following?.cancel()
        following = null
        (playerRoot.parent as? ViewGroup)?.removeOnLayoutChangeListener(resized)
        holder?.let { (it.parent as? ViewGroup)?.removeView(it) }
        holder = null
        place(playerRoot, rightMargin = 0, bottomMargin = 0)
    }

    /** The panel's place for the window's size: the player's margin and the panel's view. */
    fun relayout() {
        val frame = holder ?: return
        val window = frame.parent as? View ?: return
        if (!state.shown || window.width == 0) {
            frame.visibility = View.GONE
            place(playerRoot, rightMargin = 0, bottomMargin = 0)
            return
        }
        val density = window.resources.displayMetrics.density
        val width = (window.width / density).dp
        val height = (window.height / density).dp
        val dock = PanelDock.of(width, height)
        val params = frame.layoutParams as FrameLayout.LayoutParams
        if (dock == PanelDock.Side) {
            val side = PanelDock.SideWidth.px(density)
            params.width = side
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.END
            place(playerRoot, rightMargin = side, bottomMargin = 0)
            layout = PanelLayout(dock, 1)
        } else {
            val bottom = PanelDock.bottomHeight(height).px(density)
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = bottom
            params.gravity = Gravity.BOTTOM
            place(playerRoot, rightMargin = 0, bottomMargin = bottom)
            layout = PanelLayout(dock, PanelDock.bottomColumns(width))
        }
        frame.layoutParams = params
        frame.visibility = View.VISIBLE
    }

    private fun place(view: View, rightMargin: Int, bottomMargin: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.rightMargin == rightMargin && params.bottomMargin == bottomMargin) return
        params.rightMargin = rightMargin
        params.bottomMargin = bottomMargin
        view.layoutParams = params
    }

    private fun Dp.px(density: Float): Int = (value * density).roundToInt()
}

/** The panel's view: the keys stay the player's (Space, the arrows, the shortcuts), as over the video. */
@SuppressLint("ViewConstructor")
internal class PanelFrame(context: Context) : FrameLayout(context) {
    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
    }
}
