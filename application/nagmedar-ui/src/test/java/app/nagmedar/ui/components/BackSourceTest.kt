package app.nagmedar.ui.components

import androidx.activity.BackEventCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.0.4: whose Back a predictive Back is ([BackSource]). A finger's needs an edge it came from and a system that
 * keeps the window's edges for it; the Back button (no edge since Android 16), a key, and anything at all on a
 * phone with three buttons are not a finger's.
 */
class BackSourceTest {

    @Test
    fun aFingersBackComesFromAnEdgeOfAWindowWhoseEdgesTakeTheSwipe() {
        assertTrue(BackSource.finger(BackEventCompat.EDGE_LEFT, edgeSwipes = true))
        assertTrue(BackSource.finger(BackEventCompat.EDGE_RIGHT, edgeSwipes = true))
    }

    @Test
    fun theBackButtonAndAKeyAreNoFinger() {
        assertFalse(BackSource.finger(BackEventCompat.EDGE_NONE, edgeSwipes = true))
        assertFalse(BackSource.finger(BackEventCompat.EDGE_NONE, edgeSwipes = false))
    }

    @Test
    fun withThreeButtonsNothingIsAFingerWhateverEdgeItNames() {
        assertFalse(BackSource.finger(BackEventCompat.EDGE_LEFT, edgeSwipes = false))
        assertFalse(BackSource.finger(BackEventCompat.EDGE_RIGHT, edgeSwipes = false))
    }
}
