package app.nagmedar.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** S-302: the left Alt alone or F10 is the menu bar's key; Alt in a chord and AltGr are not. */
class MenuBarKeyTest {

    private fun keys(vararg events: Pair<Int, Boolean>): List<Boolean> {
        val key = MenuBarKey()
        return events.map { (code, down) -> key.onKey(code, down) }
    }

    private val altDown = KeyEvent.KEYCODE_ALT_LEFT to true
    private val altUp = KeyEvent.KEYCODE_ALT_LEFT to false

    @Test
    fun altAloneOpensTheMenuBarOnRelease() {
        assertEquals(listOf(false, true), keys(altDown, altUp))
        // held: the repeated downs change nothing
        assertEquals(listOf(false, false, false, true), keys(altDown, altDown, altDown, altUp))
    }

    @Test
    fun altWithAnotherKeyIsAChord() {
        assertEquals(
            listOf(false, false, false, false),
            keys(altDown, KeyEvent.KEYCODE_ENTER to true, KeyEvent.KEYCODE_ENTER to false, altUp),
        )
        // the next Alt alone works again
        val key = MenuBarKey()
        key.onKey(KeyEvent.KEYCODE_ALT_LEFT, true)
        key.onKey(KeyEvent.KEYCODE_S, true)
        key.onKey(KeyEvent.KEYCODE_ALT_LEFT, false)
        key.onKey(KeyEvent.KEYCODE_ALT_LEFT, true)
        assertEquals(true, key.onKey(KeyEvent.KEYCODE_ALT_LEFT, false))
    }

    @Test
    fun f10OpensItAndAltGrDoesNot() {
        assertEquals(listOf(true, false), keys(KeyEvent.KEYCODE_F10 to true, KeyEvent.KEYCODE_F10 to false))
        assertEquals(listOf(false, false), keys(KeyEvent.KEYCODE_ALT_RIGHT to true, KeyEvent.KEYCODE_ALT_RIGHT to false))
    }
}
