package app.nagmedar.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * When the player's controls show and in which layout (S-601). They are the player's only
 * controls: a mouse moving over the video brings their desktop layout, which hides
 * [HIDE_AFTER_MS] after the mouse stops unless it rests on them or one of their menus is open;
 * a finger or a pen brings their [touch] layout, which a tap shows and hides and which hides
 * [TOUCH_HIDE_AFTER_MS] after the last touch. The cursor hides with them; TalkBack keeps them
 * shown; the touch lock ([locked]) puts them and the gestures away; in picture in picture there
 * are none. Where their bars, cards and panel are ([surfaces]) the wheel is theirs.
 */
@Stable
class ChromeVisibility(touch: Boolean = false) {
    /** The bars, the cards and the panel in the window, as they are laid out. */
    val surfaces = ChromeSurfaces()

    /** A finger or a pen touched the video last: the touch layout, its gestures and its larger targets. */
    var touch by mutableStateOf(touch)
        private set

    var visible by mutableStateOf(false)
        private set

    var pictureInPicture by mutableStateOf(false)
        private set

    /** The touch lock: no controls and no gestures but the lock's own button. */
    var locked by mutableStateOf(false)
        private set

    /** The pointer is on the bars: they stay. */
    var overBars by mutableStateOf(false)

    /** A menu of the bars, or one of the touch layout's sheets, is open: they stay. */
    var menuOpen by mutableStateOf(false)

    /** TalkBack explores the screen by touch: the controls stay while it does. */
    var explored by mutableStateOf(false)

    /** Counts what asked for the controls while locked: each shows the lock's button again for a while. */
    var lockHints by mutableIntStateOf(0)
        private set

    /** Counts the moves and the touches: each starts the hide timer again. */
    var moves by mutableIntStateOf(0)
        private set

    /** The cursor hides with the controls over a playing video. */
    val cursorHidden: Boolean get() = !touch && !visible && !pictureInPicture

    /** Whether the hide timer runs. */
    val hiding: Boolean get() = visible && !overBars && !menuOpen && !explored

    fun onMouseMoved() {
        touch = false
        show()
    }

    /** A finger or a pen touched the video: the touch layout takes over; a tap then shows it. */
    fun onTouched() {
        if (touch) return
        touch = true
        overBars = false
    }

    /** Shows the controls for a while: a move, a touch, or the player asked for its overlay; locked, the lock's button. */
    fun show() {
        if (pictureInPicture) return
        if (locked) {
            lockHints++
            return
        }
        visible = true
        moves++
    }

    /** Something was touched on the controls: the timer starts again, nothing else changes. */
    fun poke() {
        if (visible) moves++
    }

    fun hide() {
        visible = false
        overBars = false
    }

    /** A tap on the video: the touch layout shows, or goes. */
    fun toggle() = if (visible) hide() else show()

    /** The touch lock (S-601): the controls go, and only the lock's button comes back when the video is tapped. */
    fun lock() {
        locked = true
        menuOpen = false
        hide()
    }

    fun unlock() {
        locked = false
        show()
    }

    fun onPictureInPicture(inPictureInPicture: Boolean) {
        pictureInPicture = inPictureInPicture
        if (inPictureInPicture) {
            hide()
            menuOpen = false
        }
    }

    companion object {
        /** S-601: the desktop controls hide two seconds after the mouse stops. */
        const val HIDE_AFTER_MS = 2_000L

        /** The touch layout hides four seconds after the last touch, as VLC's overlay does. */
        const val TOUCH_HIDE_AFTER_MS = 4_000L
    }
}
