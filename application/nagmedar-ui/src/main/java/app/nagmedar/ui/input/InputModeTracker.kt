package app.nagmedar.ui.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.InputMode

/**
 * Tracks whether the user drives the app with a mouse/touchpad (pointer density) or with
 * fingers (touch density). Starts from the connected devices (a mouse or touchpad that is switched
 * on: DeX, the keyboard cover) and follows them as they come, go or are switched on and off
 * (1.0.3, Desktop Kit 2.3's M4); then follows the last input: a mouse move or click (a touchpad
 * counts as a mouse) switches to [InputMode.Pointer], a finger touch back to [InputMode.Touch].
 * Stylus input keeps the current mode.
 */
@Stable
class InputModeState(initial: InputMode) {
    var detected by mutableStateOf(initial)
        internal set

    fun effective(preference: DensityPreference): InputMode = when (preference) {
        DensityPreference.Auto -> detected
        DensityPreference.Touch -> InputMode.Touch
        DensityPreference.Pointer -> InputMode.Pointer
    }

    internal fun onPointerType(type: PointerType) {
        when (type) {
            PointerType.Mouse -> detected = InputMode.Pointer
            PointerType.Touch -> detected = InputMode.Touch
            else -> Unit
        }
    }
}

@Composable
fun rememberInputModeState(): InputModeState {
    val context = LocalContext.current
    val state = remember { InputModeState(if (hasPrecisePointer(context)) InputMode.Pointer else InputMode.Touch) }
    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val devices = PointerDevices(InputDevice.getDeviceIds()) { isPrecisePointer(InputDevice.getDevice(it)) }
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                devices.added(deviceId)?.let { state.detected = it }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                devices.removed(deviceId)?.let { state.detected = it }
            }

            // the keyboard cover's touchpad switched on or off, a mouse woken up
            override fun onInputDeviceChanged(deviceId: Int) {
                devices.changed(deviceId)?.let { state.detected = it }
            }
        }
        inputManager?.registerInputDeviceListener(listener, null)
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }
    return state
}

/**
 * 1.0.3 (Desktop Kit 2.3's M4): the mice and touchpads that are switched on, by device id. A
 * device's change counts only when it switches one on or off: the other changes a keyboard cover
 * reports (a new layout, a reconfigured key map) leave the density alone, so a finger on the
 * screen keeps touch sizes. A device switched off (Samsung's keyboard cover with its touchpad
 * turned off) does not count while it is off.
 */
internal class PointerDevices(ids: IntArray, private val isPrecise: (Int) -> Boolean) {
    private val on = ids.filterTo(mutableSetOf()) { isPrecise(it) }

    /** The mode a new device calls for, or null when it changes nothing. */
    fun added(id: Int): InputMode? = if (isPrecise(id) && on.add(id)) InputMode.Pointer else null

    /** Touch once the last mouse or touchpad that was on has gone; null otherwise. */
    fun removed(id: Int): InputMode? = if (on.remove(id) && on.isEmpty()) InputMode.Touch else null

    /** Pointer when a device is switched on (or wakes up as a mouse), touch when the last one is switched off. */
    fun changed(id: Int): InputMode? {
        val now = isPrecise(id)
        return when {
            now && on.add(id) -> InputMode.Pointer
            !now && on.remove(id) && on.isEmpty() -> InputMode.Touch
            else -> null
        }
    }
}

/** Observes every pointer event in the initial pass, without consuming it, to update [state]. */
fun Modifier.trackInputMode(state: InputModeState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            state.onPointerType(change.type)
        }
    }
}

internal fun hasPrecisePointer(context: Context): Boolean =
    InputDevice.getDeviceIds().any { isPrecisePointer(InputDevice.getDevice(it)) }

/** A mouse or touchpad that is switched on; a virtual device (the emulator's, a remote's) never is one. */
private fun isPrecisePointer(device: InputDevice?): Boolean {
    if (device == null || device.isVirtual || !device.isEnabled) return false
    val sources = device.sources
    return (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
}
