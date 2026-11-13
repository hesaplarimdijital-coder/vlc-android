package app.nagmedar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.shell.LocalShellContentPadding
import app.nagmedar.ui.shell.LocalShellOverlayHost
import app.nagmedar.ui.theme.GlassThickness
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagGlass
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.glass
import app.nagmedar.ui.theme.rememberNagHaptics

/**
 * 1.0.8 (Desktop Kit 2.8.0, DeskMail's request 14): pull down to refresh. A finger pulling a list down past its top
 * brings a disc down from under the phone's top bar, its arc filling as the pull goes on; let go once the arc is whole
 * (past [threshold], with a tick) and [onRefresh] runs, and the disc turns while [refreshing], then goes back up. It
 * also comes down by itself while [refreshing] (a refresh started elsewhere). Wrap the list with it: a `LazyColumn`, a
 * [DenseTable], a grid.
 *
 * A mouse, a touchpad and the keyboard leave it alone ([enabled] is for touch by default): they have the screen's own
 * button and F5. In the shell the disc comes out under the phone's top bar where the list lies beneath it, else at the
 * list's own top (a field or tabs above the list); nothing of it shows above the list. With reduced motion it jumps to
 * its place and back. TalkBack reads "Yenileniyor" while it turns.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = NagTheme.inputMode == InputMode.Touch,
    threshold: Dp = 80.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberNagHaptics()
    val latestRefresh by rememberUpdatedState(onRefresh)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val pull = remember(density, threshold) {
        PullState(
            thresholdPx = with(density) { threshold.toPx() },
            restPx = with(density) { (threshold * REST).toPx() },
            refreshing = { latestRefreshing },
            onRefresh = { latestRefresh() },
            onArmed = { haptics.threshold() },
        )
    }
    pull.enabled = enabled
    pull.tier = if (NagTheme.motion.reduced) MotionTier.Reduced else LocalMotionTier.current
    // down by itself while refreshing, up once done
    LaunchedEffect(refreshing, pull) { pull.settle() }
    // Where the disc comes out: under the phone's top bar when the list lies beneath it, else at the list's own top (the
    // shell pads the screen, or the screen put a field or tabs above the list). The bar covers the shell's top padding,
    // counted from the top of the shell's overlay.
    val overlay = LocalShellOverlayHost.current
    val shellPadding = LocalShellContentPadding.current
    val boxTop = remember { mutableFloatStateOf(0f) }
    val top: Density.() -> Float = {
        val shellTop = overlay?.coordinates?.takeIf { it.isAttached }?.positionInRoot()?.y ?: boxTop.floatValue
        (shellPadding.calculateTopPadding().toPx() - (boxTop.floatValue - shellTop)).coerceAtLeast(0f)
    }
    Box(modifier.nestedScroll(pull).onGloballyPositioned { boxTop.floatValue = it.positionInRoot().y }) {
        content()
        RefreshDisc(pull, refreshing, top)
    }
}

/** Where the disc rests while refreshing, as a share of the threshold. */
private const val REST = 0.75f

/** One turn of the disc while refreshing. */
private const val TURN_MS = 1000

/** How much of a pull past the top moves the disc: it resists. */
private const val RESISTANCE = 0.5f

@Stable
private class PullState(
    val thresholdPx: Float,
    val restPx: Float,
    val refreshing: () -> Boolean,
    val onRefresh: () -> Unit,
    val onArmed: () -> Unit,
) : NestedScrollConnection {
    var enabled = true

    /** With reduced motion the disc jumps to its place instead of sliding there. */
    var tier = MotionTier.Timed

    /** How far the disc has come down, in pixels. */
    var distance by mutableFloatStateOf(0f)
        private set

    /** The pull as a share of the threshold, 0…1. */
    val progress: Float get() = (distance / thresholdPx).coerceIn(0f, 1f)

    fun pulledTo(value: Float) {
        val wasArmed = distance >= thresholdPx
        distance = value.coerceIn(0f, thresholdPx * 2f)
        if (!wasArmed && distance >= thresholdPx && !refreshing()) onArmed()
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // pushing back up takes the disc up before the list moves
        if (source != NestedScrollSource.UserInput || available.y >= 0f || distance <= 0f) return Offset.Zero
        val taken = maxOf(available.y, -distance / RESISTANCE)
        pulledTo(distance + taken * RESISTANCE)
        return Offset(0f, taken)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // the list is at its top: what is left of the pull brings the disc down
        if (!enabled || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        pulledTo(distance + available.y * RESISTANCE)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (distance <= 0f) return Velocity.Zero
        if (distance >= thresholdPx && !refreshing()) {
            onRefresh()
            // to the resting place at once: the screen says it is refreshing on its next frame
            animateTo(restPx)
        }
        settle()
        // the fling of a pull is the pull's: the list does not fling on
        return available
    }

    /** To the resting place while refreshing, else back up. */
    suspend fun settle() = animateTo(if (refreshing()) restPx else 0f)

    private suspend fun animateTo(target: Float) {
        if (distance == target) return
        animate(distance, target, animationSpec = NagMotion.fade(tier, NagMotion.page(NagMotion.PIXEL))) { value, _ -> distance = value }
    }
}

/** The disc, [top] pixels under the list's top at the start of a pull (where it comes out) and lower as the pull goes on. */
@Composable
private fun BoxScope.RefreshDisc(pull: PullState, refreshing: Boolean, top: Density.() -> Float) {
    val colors = NagTheme.colors
    val tier = LocalMotionTier.current
    val reduced = NagTheme.motion.reduced
    val label = stringResource(R.string.nag_refreshing)
    // the pull itself is read while drawing: only its start and end recompose
    val pulled by remember(pull) { derivedStateOf { pull.distance > 0f } }
    if (!pulled && !refreshing) return
    // turns while refreshing and stops with it (with reduced motion it rests)
    val spin = remember { Animatable(0f) }
    LaunchedEffect(refreshing, tier, reduced) {
        if (!refreshing || tier == MotionTier.Reduced || reduced) return@LaunchedEffect
        while (true) {
            // an endless animation as far as tests go (InfiniteAnimationPolicy): they stop it here
            withInfiniteAnimationFrameNanos { }
            spin.snapTo(0f)
            spin.animateTo(360f, tween(TURN_MS, easing = LinearEasing))
        }
    }
    val accent = colors.accent
    val track = colors.border
    val disc = 40.dp
    // nothing of it shows above the list (over a field or tabs the screen put above it)
    Box(Modifier.matchParentSize().clipToBounds()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // out from under the top bar, its edge first
                    translationY = top() + pull.distance - disc.toPx()
                    alpha = if (refreshing) 1f else (pull.progress * 1.5f).coerceAtMost(1f)
                }
                .size(disc)
                .glass(NagGlass.raised(colors, GlassThickness.Regular), CircleShape)
                .border(1.dp, colors.borderStrong, CircleShape)
                .testTag("refresh-disc")
                .then(
                    if (!refreshing) Modifier else Modifier.semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val progress = pull.progress
                // the arc fills while the finger pulls and turns once refreshing
                val sweep = if (refreshing) 270f else 300f * progress
                rotate(if (refreshing) spin.value else progress * 180f) {
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(accent, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
    }
}
