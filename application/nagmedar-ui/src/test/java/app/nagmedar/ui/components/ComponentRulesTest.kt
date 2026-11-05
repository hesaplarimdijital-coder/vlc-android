package app.nagmedar.ui.components

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.nagmedar.ui.theme.MotionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.0.3 (Desktop Kit 2.1–2.3), the components' rules free of Compose: where a menu opens and grows
 * from, what a click on a list's item does by the press that made it, when Back clears a selection,
 * what a swiped notice does when let go, how deep a phone's press goes.
 */
class ComponentRulesTest {

    private val window = IntSize(400, 1000)
    private val menu = IntSize(220, 200)

    @Test
    fun aDropDownNearTheBottomOpensAboveItsButton() {
        // a 48 px button 52 px above the window's bottom; the menu was asked for under it
        val button = IntRect(20, 900, 180, 948)
        val position = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2)).calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals("the menu ends at the button's top", button.top - menu.height, position.y)
        assertEquals(button.left, position.x)
    }

    @Test
    fun aMenuAtAPointNearTheBottomOpensAboveThePoint() {
        // a long press 20 px into a row near the bottom
        val row = IntRect(0, 880, 400, 960)
        val position = ClampedOffsetPositionProvider(IntOffset(150, 20)).calculatePosition(row, window, LayoutDirection.Ltr, menu)
        assertEquals(row.top + 20 - menu.height, position.y)
        assertEquals(150, position.x)
    }

    @Test
    fun aMenuThatFitsOpensUnderItsButtonAndGrowsFromItsTopLeft() {
        val button = IntRect(20, 100, 180, 148)
        val provider = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
        val position = provider.calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals(button.bottom + 2, position.y)
        assertEquals(TransformOrigin(0f, 0f), provider.origin)
    }

    @Test
    fun aMovedMenuGrowsFromThePointItWasOpenedAt() {
        val button = IntRect(20, 900, 180, 948)
        val flipped = ClampedOffsetPositionProvider(IntOffset(0, button.height + 2))
        flipped.calculatePosition(button, window, LayoutDirection.Ltr, menu)
        assertEquals("flipped above: from its bottom-left corner", TransformOrigin(0f, 1f), flipped.origin)
        // a long press 300 px from the left in a 400 px window: the 220 px menu is pushed left by 120 px
        val row = IntRect(0, 100, 400, 180)
        val pushed = ClampedOffsetPositionProvider(IntOffset(300, 20))
        assertEquals(180, pushed.calculatePosition(row, window, LayoutDirection.Ltr, menu).x)
        assertEquals("pushed in: from under the finger", 120f / 220f, pushed.origin.pivotFractionX, 0.001f)
        assertEquals(0f, pushed.origin.pivotFractionY, 0.001f)
    }

    @Test
    fun theMenusPlaceStaysWithItsOpeningAndEachOpeningIsNew() {
        val state = MenuState()
        state.open(IntOffset(10, 20))
        val first = state.generation
        state.close()
        assertFalse(state.isOpen)
        assertEquals("closing is no new opening", first, state.generation)
        state.open(IntOffset(10, 20))
        assertTrue("opened again, even at the same place, it is a new pop-up", state.generation > first)
    }

    @Test
    fun aFingersTapOpensAMouseClickSelectsWhateverTheLayout() {
        // the press decides
        assertEquals(ListClick.Open, ListClick.of(selecting = false, finger = true, touchLayout = false))
        assertEquals(ListClick.Select, ListClick.of(selecting = false, finger = false, touchLayout = true))
        // without a press (a key, TalkBack) the layout does
        assertEquals(ListClick.Open, ListClick.of(selecting = false, finger = null, touchLayout = true))
        assertEquals(ListClick.Select, ListClick.of(selecting = false, finger = null, touchLayout = false))
        // while picking ("Seç") every tap and click takes an item in or out
        assertEquals(ListClick.Toggle, ListClick.of(selecting = true, finger = true, touchLayout = true))
        assertEquals(ListClick.Toggle, ListClick.of(selecting = true, finger = false, touchLayout = false))
    }

    @Test
    fun theClickModifiersForgetThePressOnceRead() {
        val modifiers = ClickModifiers()
        modifiers.pressed(ctrl = true, shift = false, finger = false)
        assertEquals(false, modifiers.takeFinger())
        assertEquals(null, modifiers.takeFinger())
        assertEquals(true to false, modifiers.take())
        assertEquals(false to false, modifiers.take())
    }

    @Test
    fun backClearsASelectionBeingMade() {
        // by touch or while picking, any selection
        assertTrue(SelectionRules.backClears(count = 1, selecting = false, touchLayout = true))
        assertTrue(SelectionRules.backClears(count = 1, selecting = true, touchLayout = false))
        assertTrue("picking with nothing picked yet", SelectionRules.backClears(count = 0, selecting = true, touchLayout = false))
        // with a mouse several items, not the one clicked
        assertTrue(SelectionRules.backClears(count = 2, selecting = false, touchLayout = false))
        assertFalse(SelectionRules.backClears(count = 1, selecting = false, touchLayout = false))
        // nothing selected: Back goes back
        assertFalse(SelectionRules.backClears(count = 0, selecting = false, touchLayout = true))
    }

    @Test
    fun aSwipedNoticeCarriesOnOnAPhoneAndGoesWhereItIsWiderUp() {
        val width = 300
        assertEquals(SwipeDismissRules.Release.CarryOut, SwipeDismissRules.release(-120f, -200f, width, MotionTier.Gesture))
        assertEquals("a fling, however short", SwipeDismissRules.Release.CarryOut, SwipeDismissRules.release(20f, 1500f, width, MotionTier.Gesture))
        assertEquals(SwipeDismissRules.Release.Dismiss, SwipeDismissRules.release(-120f, 0f, width, MotionTier.Timed))
        assertEquals(SwipeDismissRules.Release.Dismiss, SwipeDismissRules.release(-120f, 0f, width, MotionTier.Reduced))
        assertEquals("short and slow: back", SwipeDismissRules.Release.Back, SwipeDismissRules.release(60f, 300f, width, MotionTier.Gesture))
    }

    @Test
    fun aPhonesPressGoesThreeQuartersAsDeep() {
        assertEquals(0.97f, phonePressScale(0.96f), 0.0001f)
        assertEquals(0.94f, phonePressScale(0.92f), 0.0001f)
        assertEquals(1f, phonePressScale(1f), 0.0001f)
    }

    @Test
    fun theScreensCommandsAreSilentWhileCovered() {
        val commands = ScreenCommands()
        var selected = 0
        commands.selectAllHandler = {
            selected++
            true
        }
        assertTrue(commands.selectAll())
        val layer = Any()
        commands.cover(layer)
        assertTrue(commands.covered)
        assertFalse("Ctrl+A under a layer over the window", commands.selectAll())
        commands.uncover(layer)
        assertTrue(commands.selectAll())
        assertEquals(2, selected)
    }
}
