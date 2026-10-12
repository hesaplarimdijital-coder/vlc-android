package app.nagmedar.ui

import android.view.KeyEvent
import app.nagmedar.ui.input.MenuBarKey
import app.nagmedar.ui.input.ShortcutRegistry
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * The window's keys (S-401, S-302, S-407, S-408): what NagmedarActivity does with a key before the
 * views see it and after none of them used it. Apart from the activity so that the end-to-end
 * tests (S-1403) send their keys through the same rules.
 *
 * Chords with Ctrl, Alt or Meta are shortcuts before the views; plain keys (Space, N, P, M …)
 * only when no focused element used them, and the plain arrows move the focus. Alt alone or F10
 * takes the keyboard to the menu bar and back.
 */
class WindowKeys(private val actions: () -> AppActions?, private val goBack: () -> Boolean) {

    private val menuBarKey = MenuBarKey()

    /** Before the views: the menu bar's keys, then the shortcuts with a modifier. True when used. */
    fun beforeViews(event: KeyEvent, rtl: Boolean): Boolean {
        // the shortcut editor is recording keys (S-402)
        if (ShortcutRegistry.recording) return false
        val actions = actions()
        val down = event.action == KeyEvent.ACTION_DOWN
        if (menuBarKey.onKey(event.keyCode, down) && actions?.toggleMenuBar() == true) return true
        if (actions?.menuBarKey(event.keyCode, down, rtl) == true) return true
        if (down && (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed)) {
            val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
            // Ctrl+A and Ctrl+Z go to a focused text field first (afterViews)
            if (shortcut != null && !shortcut.textFirst && actions?.perform(shortcut.id) == true) return true
        }
        return false
    }

    /**
     * After the views, a key pressed that none of them used: a plain shortcut; Esc, which lets go of
     * the screen's selection (S-405) or goes back (S-407), never out of the app; the forward key of
     * some keyboards (S-408). True when used.
     */
    fun afterViews(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val actions = actions()
        // the plain arrows move the focus in the shell; bound to a shortcut (mpv's seek) they act in the player
        if (!ShortcutRegistry.recording && !(isArrow(keyCode) && event.hasNoModifiers())) {
            val shortcut = ShortcutRegistry.match(ComposeKeyEvent(event))
            if (shortcut != null && (shortcut.chord?.hasModifier != true || shortcut.textFirst) && actions?.perform(shortcut.id) == true) return true
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers()) {
            if (actions?.escape() == true || goBack()) return true
        }
        return keyCode == KeyEvent.KEYCODE_FORWARD && actions?.forward() == true
    }

    private fun isArrow(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
}
