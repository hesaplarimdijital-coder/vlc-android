package app.nagmedar.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3 (Desktop Kit 2.3) frame by frame, as the kit's own sheet tests: a phone's sheet flung down carries on at
 * the finger's speed and is dismissed only once it is out, never pausing on the way; wider windows close it at
 * once and it goes from where it is; it comes and goes on one firm spring; the drag past the closing point ticks
 * once by touch and never with a mouse; an on/off action is a switch that flips and leaves the sheet open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class SheetMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)
    private var dismissed = 0

    private val felt = mutableListOf<HapticFeedbackType>()
    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            felt += hapticFeedbackType
        }
    }

    private fun sheet(widthClass: WidthClass = WidthClass.Compact, mode: InputMode = InputMode.Touch, groups: () -> List<ActionGroup> = { rows }) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = mode) {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    TestShell(widthClass, screen = { _, _ ->
                        Box(Modifier.fillMaxSize()) {
                            // a sheet whatever the width: the phone's, and the same sheet in a tablet's window
                            GlassBottomSheet(visible = open, onDismiss = {
                                dismissed++
                                open = false
                            }, label = "Sabaha Kadar") {
                                ActionGroupList(groups()) {
                                    dismissed++
                                    open = false
                                }
                            }
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    private val rows = listOf(ActionGroup(null, listOf("Oynat", "Sıradaki olarak çal", "Sıraya ekle", "Çalma listesine ekle…", "Albüme git", "Bilgi").map { MenuEntry.Item(it, {}) }))

    private fun top(): Float = compose.onNodeWithTag("sheet").getBoundsInRoot().top.value

    /** A fling carries the sheet out at its own speed, frame by frame; the dismiss comes once it is gone. */
    @Test
    fun aFlingCarriesTheSheetOutBeforeItIsDismissed() {
        sheet()
        val windowBottom = compose.onRoot().getBoundsInRoot().bottom.value
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Sıraya ekle").performTouchInput { swipe(center, center + Offset(0f, 150f), durationMillis = 60) }
        compose.waitForIdle()
        assertEquals("not dismissed while still on its way out", 0, dismissed)
        var last = top()
        var frames = 0
        var moved = 0
        var still = 0
        while (dismissed == 0 && frames < 90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
            if (dismissed == 0) {
                val now = top()
                assertTrue("frame $frames: the sheet came back up ($last → $now)", now >= last - 0.01f)
                // after its first frame no pause of two frames while it is in view
                if (now > last) {
                    moved++
                    still = 0
                } else if (now < windowBottom) {
                    still++
                    assertTrue("frame $frames: the sheet stood still at $now", still <= 1)
                }
                last = now
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertTrue("the sheet moved on its own after the finger lifted ($moved frames)", moved >= 3)
        // nothing left of it: no way down played from where it stood
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    /** Wider up (the timed tier) the fling closes it at once, and it goes from where the finger left it, as in 1.0.2. */
    @Test
    @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun onATabletAFlingClosesAtOnceAndItGoesFromWhereItIs() {
        sheet(WidthClass.Expanded)
        val rest = top()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Sıraya ekle").performTouchInput { swipe(center, center + Offset(0f, 150f), durationMillis = 60) }
        compose.waitForIdle()
        val released = top()
        assertTrue("the finger had pulled it down ($rest → $released)", released > rest + 10f)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("dismissed the moment it was let go", 1, dismissed)
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue("it goes on down from where it was let go", top() >= released - 0.5f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    /** The tick's rule, finger by finger: once each time a pull passes a third of the sheet, never on the way back. */
    @Test
    fun theTickComesOncePerCrossing() {
        var ticks = 0
        val drag = SheetDrag(CoroutineScope(Job()), flingClosePx = 1200f)
        drag.height = 900
        drag.onThreshold = { ticks++ }
        drag.held = true
        drag.pull(200f)
        assertEquals("short of a third", 0, ticks)
        drag.pull(200f)
        assertEquals("past it", 1, ticks)
        drag.pull(50f)
        assertEquals("further on: no more", 1, ticks)
        drag.pull(-200f)
        assertEquals("back above it", 1, ticks)
        drag.pull(100f)
        assertEquals("past it again", 2, ticks)
        // let go and caught again down there: the new press starts past it, no tick where it already is
        drag.held = false
        drag.held = true
        drag.pull(10f)
        assertEquals(2, ticks)
    }

    /** A slow pull past the closing point (a third of the sheet), as a finger does it: one tick on the way, by touch only. */
    @Test
    fun aPullPastTheClosingPointTicksOnce() {
        sheet()
        pullFarDown()
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), felt)
        // past a third and let go: it closed
        assertEquals(1, dismissed)
    }

    @Test
    fun withAMouseNothingTicks() {
        sheet(mode = InputMode.Pointer)
        pullFarDown()
        assertEquals(emptyList<HapticFeedbackType>(), felt)
        assertEquals(1, dismissed)
    }

    /** Most of the sheet's height down from its first row, slowly (no fling: only the distance closes it). */
    private fun pullFarDown() {
        val sheetDp = compose.onNodeWithTag("sheet").getBoundsInRoot().let { (it.bottom - it.top).value }
        compose.onNodeWithText("Oynat").performTouchInput {
            swipe(center, center + Offset(0f, sheetDp * 0.8f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
    }

    /** An on/off action is a switch at the row's end: it flips, a screen reader reads a switch, and the sheet stays. */
    @Test
    fun anOnOffActionIsASwitchThatLeavesTheSheetOpen() {
        var follow by mutableStateOf(false)
        sheet(groups = {
            listOf(
                ActionGroup(
                    "Oynatma",
                    listOf(
                        MenuEntry.Item("Şarkı sözlerini göster", { follow = !follow }, checked = follow),
                        MenuEntry.Item("Bilgi", {}),
                    ),
                ),
            )
        })
        val toggle = compose.onNodeWithText("Şarkı sözlerini göster")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        toggle.performClick()
        compose.waitForIdle()
        assertTrue(follow)
        assertEquals("a switch keeps the sheet open", 0, dismissed)
        assertTrue(open)
        compose.onNodeWithText("Şarkı sözlerini göster")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // by touch it vibrates as it turns on
        assertEquals(listOf(HapticFeedbackType.ToggleOn), felt)
        // any other action closes it
        compose.onNodeWithText("Bilgi").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    /** The same switch in a MoreSheet, whose rows are kept while it goes down: it shows its new state while open. */
    @Test
    fun aMoreSheetsSwitchShowsItsNewState() {
        var follow by mutableStateOf(false)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, screen = { _, _ ->
                    Box(Modifier.fillMaxSize()) {
                        MoreSheet(
                            visible = open,
                            onDismiss = {
                                dismissed++
                                open = false
                            },
                            groups = listOf(ActionGroup(null, listOf(MenuEntry.Item("Kapak rengini izle", { follow = !follow }, checked = follow)))),
                            title = "Görünüm",
                        )
                    }
                })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Kapak rengini izle").performClick()
        compose.waitForIdle()
        assertTrue(follow)
        assertEquals(0, dismissed)
        compose.onNodeWithText("Kapak rengini izle").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun theSheetsWayRules() {
        // the phone: one firm spring both ways, arrived within a thousandth
        val phone = sheetWay(MotionTier.Gesture, tween(300))
        assertTrue(phone is SpringSpec<*>)
        phone as SpringSpec<*>
        assertEquals(0.92f, phone.dampingRatio)
        assertEquals(800f, phone.stiffness)
        assertEquals(NagMotion.FRACTION, phone.visibilityThreshold)
        // wider up the timed curve given, a jump with reduced motion
        assertTrue(sheetWay(MotionTier.Timed, tween(300)) is TweenSpec<*>)
        assertTrue(sheetWay(MotionTier.Reduced, tween(300)) is SnapSpec<*>)
        // carried out by the finger only on a phone
        assertTrue(SheetDragRules.carriesOut(MotionTier.Gesture))
        assertFalse(SheetDragRules.carriesOut(MotionTier.Timed))
        assertFalse(SheetDragRules.carriesOut(MotionTier.Reduced))
    }
}
