package app.nagmedar.ui.player.panel

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
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
import app.nagmedar.ui.player.ChromeSurfaces
import app.nagmedar.ui.theme.NagMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What the panel's view shows: its dock and, under the video, how many columns its sections take. */
class PanelLayout(val dock: PanelDock, val columns: Int)

/**
 * Docks the player's panel beside or under the classic player (S-603). The player's root view
 * makes room for it with a margin, so the video and the classic overlay shrink next to it rather
 * than under it; the panel is its own view in the window's content, so a finger reaches it as a
 * mouse does. The window's size decides the side ([PanelDock.of]) and follows a resized window.
 * While it shows it is one of the controls' [surfaces]: the wheel over it scrolls it. It slides in
 * from the edge it docks to over [NagMotion.LONG] and out over [NagMotion.MEDIUM], the player's
 * margin following it frame by frame, so the video moves with it rather than jumping (the system's
 * animator scale applies; with animations removed it is there at once).
 */
internal class PanelHost(
    private val owner: LifecycleOwner,
    private val playerRoot: View,
    private val state: PanelState,
    private val surfaces: ChromeSurfaces,
    private val content: @Composable (PanelLayout) -> Unit,
) {
    private var holder: PanelFrame? = null
    private var following: Job? = null
    private var layout by mutableStateOf(PanelLayout(PanelDock.Side, 1))

    /** Where the panel docks and how deep it is there, in pixels; null before it first showed. */
    private var docked: Docked? = null

    /** How far the panel is in: 0 out of sight, 1 docked; between while it slides. */
    private var fraction = 0f
    private var slide: ValueAnimator? = null

    /** Where the running [slide] goes. */
    private var slideTarget = 0f
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
        surfaces.addView(frame)
        holder = frame
        following = owner.lifecycleScope.launch {
            snapshotFlow { state.shown }.collect { relayout() }
        }
    }

    fun detach() {
        following?.cancel()
        following = null
        slide?.let {
            it.removeAllListeners()
            it.removeAllUpdateListeners()
            it.cancel()
        }
        slide = null
        fraction = 0f
        (playerRoot.parent as? ViewGroup)?.removeOnLayoutChangeListener(resized)
        holder?.let {
            surfaces.removeView(it)
            (it.parent as? ViewGroup)?.removeView(it)
        }
        holder = null
        place(playerRoot, rightMargin = 0, bottomMargin = 0)
    }

    /** The panel's place for the window's size: the player's margin and the panel's view, slid in or out. */
    fun relayout() {
        val frame = holder ?: return
        val window = frame.parent as? View ?: return
        if (!state.shown || window.width == 0) {
            if (docked == null || window.width == 0) hide(frame) else slideTo(frame, 0f)
            return
        }
        val density = window.resources.displayMetrics.density
        val width = (window.width / density).dp
        val height = (window.height / density).dp
        val dock = PanelDock.of(width, height)
        val params = frame.layoutParams as FrameLayout.LayoutParams
        val depth: Int
        if (dock == PanelDock.Side) {
            depth = PanelDock.SideWidth.px(density)
            params.width = depth
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.END
            layout = PanelLayout(dock, 1)
        } else {
            depth = PanelDock.bottomHeight(height).px(density)
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = depth
            params.gravity = Gravity.BOTTOM
            layout = PanelLayout(dock, PanelDock.bottomColumns(width))
        }
        docked = Docked(dock == PanelDock.Side, depth)
        frame.layoutParams = params
        frame.visibility = View.VISIBLE
        slideTo(frame, 1f)
    }

    /** Slides the panel to [target] (0 out, 1 in) from where it is; a slide the other way turns back from there. */
    private fun slideTo(frame: View, target: Float) {
        val running = slide
        if (running != null) {
            // already going there: it follows the new size frame by frame
            if (slideTarget == target) return
            running.removeAllListeners()
            running.cancel()
            slide = null
        }
        if (fraction == target) {
            // in place: a resized window takes the new size at once
            if (target == 0f) hide(frame) else position(frame, 1f)
            return
        }
        val opening = target > fraction
        // the first frame already where it starts from: out of sight, the video still in the middle
        position(frame, fraction)
        slideTarget = target
        slide = ValueAnimator.ofFloat(fraction, target).apply {
            duration = ((if (opening) NagMotion.LONG else NagMotion.MEDIUM) * abs(target - fraction)).roundToLong()
            interpolator = if (opening) ARRIVING else LEAVING
            addUpdateListener { position(frame, it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (slide === animation) slide = null
                    if (target == 0f) hide(frame) else position(frame, 1f)
                }
            })
            start()
        }
    }

    /** The panel [shown] of the way in, and the player's margin as deep, so the video keeps beside it. */
    private fun position(frame: View, shown: Float) {
        val at = docked ?: return
        fraction = shown
        val margin = (at.depth * shown).roundToInt()
        val away = (at.depth - margin).toFloat()
        if (at.side) {
            frame.translationX = if (frame.layoutDirection == View.LAYOUT_DIRECTION_RTL) -away else away
            frame.translationY = 0f
            place(playerRoot, rightMargin = margin, bottomMargin = 0)
        } else {
            frame.translationX = 0f
            frame.translationY = away
            place(playerRoot, rightMargin = 0, bottomMargin = margin)
        }
    }

    private fun hide(frame: View) {
        fraction = 0f
        frame.visibility = View.GONE
        frame.translationX = 0f
        frame.translationY = 0f
        place(playerRoot, rightMargin = 0, bottomMargin = 0)
    }

    private fun place(view: View, rightMargin: Int, bottomMargin: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.rightMargin == rightMargin && params.bottomMargin == bottomMargin) return
        params.rightMargin = rightMargin
        params.bottomMargin = bottomMargin
        view.layoutParams = params
    }

    private fun Dp.px(density: Float): Int = (value * density).roundToInt()

    /** Where the panel docks: beside the video ([side]) or under it, [depth] pixels deep. */
    private class Docked(val side: Boolean, val depth: Int)

    private companion object {
        /** The motion's curves for the views: what arrives decelerates, what leaves accelerates. */
        val ARRIVING = TimeInterpolator { NagMotion.EmphasizedDecelerate.transform(it) }
        val LEAVING = TimeInterpolator { NagMotion.EmphasizedAccelerate.transform(it) }
    }
}

/** The panel's view: the keys stay the player's (Space, the arrows, the shortcuts), as over the video. */
@SuppressLint("ViewConstructor")
internal class PanelFrame(context: Context) : FrameLayout(context) {
    init {
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
    }
}
