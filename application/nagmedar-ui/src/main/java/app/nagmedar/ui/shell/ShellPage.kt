package app.nagmedar.ui.shell

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/**
 * 1.0.3: what the shell's navigation tells its pages about the transition it has just asked for ([ShellPage]), by
 * the back stack entries' ids. Written where the transitions are chosen (the page moving over another and the tabs'
 * Back gesture each say so there), read by the pages as they compose for it.
 */
@Stable
internal class ShellPages {
    /** The page that lies under the one moving over it (a page opened, a page's Back); null on a tab's change. */
    var under by mutableStateOf<String?>(null)

    /** The tab the Back gesture is leaving, shrinking with the finger; null otherwise. */
    var leaving by mutableStateOf<String?>(null)

    /** A page moves over [entry] (opened over it, or leaving it on Back). */
    fun over(entry: String) {
        under = entry
        leaving = null
    }

    /** Tabs change, by a tap or the Back button: nothing lies under anything. */
    fun tabs() {
        under = null
        leaving = null
    }

    /** The Back gesture leaves the tab [entry]. */
    fun gesture(entry: String) {
        under = null
        leaving = entry
    }
}

/** How long the tab's corners round over in the Back gesture: its shrinking's ([tabBackExit]). */
private const val TAB_CORNER_MILLIS = 300

/**
 * 1.0.3 (Desktop Kit 2.4.0): a page of the shell's navigation, [content], which draws its own page colour
 * ([ShellScreenFrame]), and what a transition does to it beyond navigation's own slide or fade:
 *
 * - [under]: the page lies under one moving over it (a page opened from it; Back from the page above). It
 *   neither travels nor fades: it sinks to [NagMotion.PAGE_UNDER_SCALE] and darkens under
 *   [NagMotion.PAGE_SCRIM] of the theme's scrim, on the slide's own timing ([NagMotion.pageDepth]), so the scrim
 *   keeps to the edge of the page above and no frame shows two pages' rows side by side on one colour. The
 *   scrim covers the whole of the page's place, the margins its sinking opens too (the shell's page colour is
 *   behind them). The predictive Back gesture seeks it with the finger like the rest of the transition.
 * - [leaving]: the tab the Back gesture is leaving rounds its corners to the detail's as it shrinks.
 *
 * Only on the layouts whose pages slide ([paged]: a phone, a rail) and only while a transition is on: the page
 * sinks in a layer of its own (its content is drawn once, the layer alone moves) and the scrim is a layer of its
 * own over it (its opacity alone changes), so nothing of the page is drawn or measured again as it moves. At rest, with reduced motion and
 * on a desktop nothing is added. The content is the first thing in the same box always, so it keeps its state
 * through every change here.
 */
@Composable
internal fun ShellPage(
    transition: Transition<EnterExitState>,
    paged: Boolean,
    under: Boolean,
    leaving: Boolean,
    tier: MotionTier,
    content: @Composable () -> Unit,
) {
    val motion = NagTheme.motion
    val scrim = NagTheme.colors.scrim
    val animated = paged && !motion.reduced && tier != MotionTier.Reduced
    // composed again on the frame a transition starts and on the one it ends
    val moving = transition.currentState != transition.targetState
    val sinks = animated && under
    val rounds = animated && leaving
    // 0 at rest, 1 all the way under the page above
    val depth = transition.animateFloat(transitionSpec = { motion.pageDepth(tier) }, label = "pageDepth") { state ->
        if (sinks && state != EnterExitState.Visible) 1f else 0f
    }
    val corner = transition.animateFloat(transitionSpec = { tween(TAB_CORNER_MILLIS, easing = LinearEasing) }, label = "tabCorner") { state ->
        if (rounds && state == EnterExitState.PostExit) 1f else 0f
    }
    val round = if (!(rounds && moving)) Modifier else Modifier.graphicsLayer {
        val c = corner.value
        if (c > 0f) {
            shape = RoundedCornerShape((DetailMotion.BACK_CORNER_DP * c).dp)
            clip = true
        }
    }
    val sink = if (!(sinks && moving)) Modifier else Modifier.graphicsLayer {
        val d = depth.value
        if (d > 0f) {
            val scale = 1f - (1f - NagMotion.PAGE_UNDER_SCALE) * d
            scaleX = scale
            scaleY = scale
        }
    }
    Box(Modifier.fillMaxSize().then(round)) {
        Box(Modifier.fillMaxSize().then(sink)) { content() }
        if (sinks && moving) {
            // a layer whose opacity alone changes: neither the scrim nor what is around it is drawn again as it moves
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = (NagMotion.PAGE_SCRIM * depth.value).coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .background(scrim),
            )
        }
    }
}
