package app.nagmedar.ui.components

import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Every dialog window of the interface. A Compose dialog closes on the system Back but not on Esc
 * of a keyboard, and its key events only reach Compose while one of its controls has the focus; so
 * this watches the window's keys: an Esc that the content did not use (a text field that clears
 * first does use it) calls [onDismissRequest], as Back does, unless [properties] ask that Back
 * does not close it.
 */
@Composable
fun NagDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        DismissOnEscape(properties.dismissOnBackPress, onDismissRequest)
        content()
    }
}

/** Puts the Esc rule above on the window of the dialog it is composed in. */
@Composable
private fun DismissOnEscape(enabled: Boolean, onDismiss: () -> Unit) {
    val view = LocalView.current
    val window = ((view.parent as? DialogWindowProvider) ?: (view as? DialogWindowProvider))?.window
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(window, enabled) {
        val original = window?.callback
        if (window == null || original == null || !enabled) return@DisposableEffect onDispose { }
        val watching = EscapeCallback(original) { dismiss() }
        window.callback = watching
        onDispose { if (window.callback === watching) window.callback = original }
    }
}

/** The window's callback, with an Esc that nothing used closing the dialog when it is pressed. */
private class EscapeCallback(private val inner: Window.Callback, private val onEscape: () -> Unit) : Window.Callback by inner {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (inner.dispatchKeyEvent(event)) return true
        if (event.keyCode != KeyEvent.KEYCODE_ESCAPE || event.modifiers != 0) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onEscape()
        return true
    }
}
