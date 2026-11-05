package app.nagmedar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Nağmedar's vibrations (Desktop Kit 2.0's M6), one word per meaning, on the system's own haptic
 * patterns, so they follow the device's touch-feedback setting and strength. Only for fingers:
 * with a mouse or a touchpad (DeX) nothing vibrates, as on a desktop.
 *
 * The app's controls use it: a switch, a check box or a filter chip turned on or off ([toggle]),
 * one of a segmented choice's options picked ([tick]), a drag past the point where letting go acts
 * ([threshold]). Something done for good is [confirm], something that could not be done [reject].
 */
@Stable
class NagHaptics internal constructor(private val feedback: HapticFeedback, private val active: () -> Boolean) {
    /** A switch, a check box or a filter chip turned on or off. */
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** One of several choices picked: a segment. */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    /** A drag crossed the point where letting go acts (close a sheet, skip a track). */
    fun threshold() = perform(HapticFeedbackType.GestureThresholdActivate)

    /** Something was done for good: saved, sent, deleted. */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** Something could not be done: a drop refused, a value out of range. */
    fun reject() = perform(HapticFeedbackType.Reject)

    private fun perform(type: HapticFeedbackType) {
        if (active()) feedback.performHapticFeedback(type)
    }
}

/** [NagHaptics] for the window's input: silent while a mouse or a touchpad drives the app. */
@Composable
fun rememberNagHaptics(): NagHaptics {
    val feedback = LocalHapticFeedback.current
    val touch by rememberUpdatedState(NagTheme.inputMode == InputMode.Touch)
    return remember(feedback) { NagHaptics(feedback) { touch } }
}
