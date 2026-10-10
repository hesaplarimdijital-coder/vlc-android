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
 * fingers (touch density). Starts from the connected devices, then follows the last input:
 * a mouse move or click switches to [InputMode.Pointer], a finger touch back to
 * [InputMode.Touch]. Stylus input keeps the current mode.
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
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                if (isPrecisePointer(InputDevice.getDevice(deviceId))) state.detected = InputMode.Pointer
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                if (!hasPrecisePointer(context)) state.detected = InputMode.Touch
            }

            override fun onInputDeviceChanged(deviceId: Int) = Unit
        }
        inputManager?.registerInputDeviceListener(listener, null)
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }
    return state
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

private fun isPrecisePointer(device: InputDevice?): Boolean {
    if (device == null || device.isVirtual) return false
    val sources = device.sources
    return (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
}
