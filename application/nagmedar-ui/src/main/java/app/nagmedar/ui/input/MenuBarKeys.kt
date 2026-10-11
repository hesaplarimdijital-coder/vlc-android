package app.nagmedar.ui.input

import android.content.res.Resources
import android.view.KeyEvent
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuSpec

/**
 * The menu bar's menus (S-302): one per [ShortcutGroup] in its order (Medya … Yardım), each item a
 * shortcut of [ShortcutRegistry] with its label and keys, so menus, tooltips and the shortcut list
 * agree. [extras] adds items that are not shortcuts at the end of a menu.
 */
fun menuBarSpecs(
    res: Resources,
    perform: (ShortcutId) -> Unit,
    available: (ShortcutId) -> Boolean,
    extras: (ShortcutGroup) -> List<MenuEntry> = { emptyList() },
): List<MenuSpec> = ShortcutGroup.entries.map { group ->
    MenuSpec(res.getString(group.title)) {
        ShortcutRegistry.defaults.filter { it.group == group }.map { shortcut ->
            MenuEntry.Item(
                label = res.getString(shortcut.label),
                onClick = { perform(shortcut.id) },
                shortcut = shortcut.chord.label(res),
                enabled = available(shortcut.id),
            )
        } + extras(group)
    }
}

/**
 * The menu bar's key, as on desktops: the left Alt pressed and released with no other key in
 * between, or F10. Alt with another key stays a chord (Alt+Enter), and the right Alt, which
 * types characters on many layouts (AltGr+Q = @), is left alone.
 */
class MenuBarKey {
    private var armed = false

    /** Feeds a key event; true when it is the menu bar's key. */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F10) return down
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT) {
            if (down) {
                // a held Alt repeats its down event; it stays armed
                if (!armed) armed = true
                return false
            }
            val alone = armed
            armed = false
            return alone
        }
        if (down) armed = false
        return false
    }
}
