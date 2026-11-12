package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import kotlin.math.exp
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

/** Lets the Back button go: the system invokes Back. */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.releaseBackButton() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
}
