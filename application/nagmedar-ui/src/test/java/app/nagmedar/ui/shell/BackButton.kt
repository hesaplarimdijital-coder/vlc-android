package app.nagmedar.ui.shell

import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 1.0.4: the Back button of the three-button navigation as Android 16 and later send it to an app (the platform's
 * BackAnimationController and BackProgressAnimator, "Add predictive back animation for 3-button-nav"): at the press
 * a predictive Back starts with no edge ([BackEventCompat.EDGE_NONE]), and for as long as the button is down its
 * progress runs by itself, on a spring without bounce of stiffness 500, from 0 towards 1; the release invokes Back.
 * A tap holds the button some 60 to 150 ms: the progress is past a third, often past four fifths, before the
 * release. The tests of 1.0.3 pressed the button as a bare `onBackPressed()` or as a start with no progress, which
 * is a keyboard's Back key; what followed the progress as a finger's (a detail, the player) went unseen.
 */
internal object BackButton {
    /** How long a tap holds the button in the tests: six frames. */
    const val TAP_FRAMES = 6

    private const val FRAME_MS = 16L
    private const val STIFFNESS = 500.0

    fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_NONE)

    /** The system's progress [ms] after the press: a critically damped spring from 0 to 1. */
    fun progress(ms: Long): Float {
        val wt = sqrt(STIFFNESS) * ms / 1000.0
        return (1.0 - (1.0 + wt) * exp(-wt)).toFloat().coerceIn(0f, 1f)
    }

    /** The progress with the [frame]th frame after the press (the first is 1). */
    fun progressAtFrame(frame: Int): Float = progress(frame * FRAME_MS)
}

/**
 * Presses the Back button and holds it for [frames] frames of the clock (which the test advances itself:
 * `mainClock.autoAdvance` false), the system's progress arriving before each; [onFrame] looks at the window after
 * every one. The button stays down: [releaseBackButton] lets it go.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.holdBackButton(frames: Int = BackButton.TAP_FRAMES, onFrame: (Int) -> Unit = {}) {
    val dispatcher = activity.onBackPressedDispatcher
    runOnUiThread { dispatcher.dispatchOnBackStarted(BackButton.event(0f)) }
    repeat(frames) { frame ->
        runOnUiThread { dispatcher.dispatchOnBackProgressed(BackButton.event(BackButton.progressAtFrame(frame + 1))) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        onFrame(frame)
    }
}

/**
 * What a system with gesture navigation tells the window: its side edges, 24 dp each, are kept for the Back swipe.
 * A test's window has none, as a phone with three buttons has none: there no predictive Back is a finger's, whatever
 * edge it names ([app.nagmedar.ui.components.BackSource]). A test of a finger's Back calls this once its content is set.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.gestureNavigation() {
    val edge = (24 * activity.resources.displayMetrics.density).roundToInt()
    val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(edge, 0, edge, 0)).build()
    runOnUiThread { ViewCompat.dispatchApplyWindowInsets(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0), insets) }
    waitForIdle()
}

/** Lets the Back button go: the system invokes Back. */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.releaseBackButton() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
}
