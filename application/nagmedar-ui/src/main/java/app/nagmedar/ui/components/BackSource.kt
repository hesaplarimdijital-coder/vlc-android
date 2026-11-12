package app.nagmedar.ui.components

import androidx.activity.BackEventCompat

/**
 * 1.0.4: whose Back a predictive Back is. Since Android 16 the Back button of the three-button navigation
 * starts one as a swipe does, but with no edge ([BackEventCompat.EDGE_NONE]) and with a progress the system
 * runs by itself, on a spring from 0 to 1, for as long as the button is down (a tap's tenth of a second takes
 * it past the half); a keyboard's Back key starts one with no progress at all. Neither is a finger: what a
 * swipe shrinks, rounds and leans (a detail, the phone's player, a tab) stays whole under a button, and the
 * release plays what a tap on the page's own arrow plays.
 *
 * 1.0.3 knew this for the tabs alone (Desktop Kit 2.4.0). A detail and the player followed the button's
 * progress as a finger's: with three buttons, Back from a podcast's episode, a show, an album, a playlist or
 * the player shrank the page into a rounded card before it left, as 1.0.2 did.
 */
internal object BackSource {
    /** A swipe from an edge of the screen: [swipeEdge] is [BackEventCompat.EDGE_LEFT] or [BackEventCompat.EDGE_RIGHT]. */
    fun finger(swipeEdge: Int): Boolean = swipeEdge != BackEventCompat.EDGE_NONE
}

/** This event of a predictive Back comes from a finger's swipe ([BackSource.finger]), not from a button or a key. */
internal val BackEventCompat.fromFinger: Boolean get() = BackSource.finger(swipeEdge)
