package app.nagmedar.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * When the desktop controls show (S-601): a mouse moving over the video shows them, and they hide
 * [HIDE_AFTER_MS] after it stops, unless it rests on them or one of their menus is open; the
 * cursor hides with them. A finger hands the video back to the classic overlay and its gestures:
 * the controls are not [active] until a mouse moves again. In picture in picture there are none.
 */
@Stable
class ChromeVisibility(active: Boolean = false) {
    /** A mouse is in use: the controls stand for the classic overlay. */
    var active by mutableStateOf(active)
        private set

    var visible by mutableStateOf(false)
        private set

    var pictureInPicture by mutableStateOf(false)
        private set

    /** The pointer is on the bars: they stay. */
    var overBars by mutableStateOf(false)

    /** A menu of the bars is open: they stay. */
    var menuOpen by mutableStateOf(false)

    /** Counts the mouse's moves: each starts the hide timer again. */
    var moves by mutableIntStateOf(0)
        private set

    /** The cursor hides with the controls over a playing video. */
    val cursorHidden: Boolean get() = active && !visible && !pictureInPicture

    /** Whether the hide timer runs. */
    val hiding: Boolean get() = visible && !overBars && !menuOpen

    /** A mouse is connected: the controls stand for the overlay from the start. */
    fun activate() {
        active = true
    }

    fun onMouseMoved() {
        active = true
        show()
    }

    /** Shows the controls for a while: the mouse moved, or the player asked for its overlay. */
    fun show() {
        if (pictureInPicture || !active) return
        visible = true
        moves++
    }

    fun hide() {
        visible = false
        overBars = false
    }

    /** A finger touched the screen: the classic overlay and its gestures take over. */
    fun onFinger() {
        active = false
        hide()
        menuOpen = false
    }

    fun onPictureInPicture(inPictureInPicture: Boolean) {
        pictureInPicture = inPictureInPicture
        if (inPictureInPicture) {
            hide()
            menuOpen = false
        }
    }

    companion object {
        /** S-601: the controls hide two seconds after the mouse stops. */
        const val HIDE_AFTER_MS = 2_000L
    }
}
