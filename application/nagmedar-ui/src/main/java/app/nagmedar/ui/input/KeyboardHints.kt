package app.nagmedar.ui.input

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 1.0.1: whether [configuration] reports a hardware keyboard at hand: a QWERTY keyboard that is
 * not hidden (DeX, a tablet's keyboard cover, a Bluetooth keyboard). The rule of every keycap and
 * shortcut label in the app (and of the dialogs' Esc, since PR #65): shown with a keyboard, left
 * out by touch alone, where they would only be noise.
 */
fun hasHardwareKeyboard(configuration: Configuration): Boolean =
    configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

/**
 * [hasHardwareKeyboard] for the window this is drawn in. The activity handles a keyboard that
 * comes or goes itself (configChanges keyboard|keyboardHidden), so the configuration read here
 * changes with it and whatever reads this follows at once.
 */
@Composable
fun hardwareKeyboard(): Boolean = hasHardwareKeyboard(LocalConfiguration.current)

/** [keys] when they are worth showing: with a hardware keyboard; null by touch alone. */
fun keyHint(keys: String?, keyboard: Boolean): String? = keys?.takeIf { keyboard && it.isNotBlank() }

/** [keyHint] for the window this is drawn in: "Ctrl+Z" with a keyboard, null without one. */
@Composable
fun keyHint(keys: String?): String? = keyHint(keys, hardwareKeyboard())
