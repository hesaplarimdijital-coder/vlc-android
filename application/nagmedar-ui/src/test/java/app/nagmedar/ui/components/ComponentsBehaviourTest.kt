package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 1.0.3 (Desktop Kit 2.0–2.3) frame by frame and touch by touch, as the kit's own tests: hover and
 * press colours at once with a mouse, easing by touch and only in opacity to and from clear; a
 * phone's lighter press (1.0.8: back without a bounce); the vibrations of the switches, check boxes, chips and
 * segments by touch only; undo bars swiped away at the finger's speed; the slider's and the text
 * field's names for TalkBack.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class ComponentsBehaviourTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------------ colours (Desktop Kit 0.2.1)

    /** The background of every composition, in order. */
    private val shown = mutableListOf<Color>()
    private var selected by mutableStateOf(false)
    private lateinit var colors: NagColors

    private fun control(mode: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = mode) {
                colors = NagTheme.colors
                val interaction = remember { MutableInteractionSource() }
                val background = stateBackground(interaction.collectState(), selected)
                SideEffect { shown += background }
                Box(Modifier.size(80.dp).testTag(CONTROL).background(background).nagClickable(interaction, {}))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** Runs frames until the background leaves [from]; its first other colour and every colour on the way (400 ms). */
    private fun change(from: Color, action: () -> Unit): Pair<Color, List<Color>> {
        shown.clear()
        action()
        var frames = 0
        while (shown.none { it != from } && frames < 10) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
        }
        val first = shown.first { it != from }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        return first to shown.filter { it != from }
    }

    private fun hoverIn() = compose.onNodeWithTag(CONTROL).performMouseInput { moveTo(center) }

    private fun hoverOut() = compose.onNodeWithTag(CONTROL).performMouseInput { moveTo(bottomRight + Offset(60f, 60f)) }

    @Test
    fun withAMouseHoverAndPressChangeAtOnce() {
        control(InputMode.Pointer)
        val (enter, enterPath) = change(Color.Transparent) { hoverIn() }
        assertEquals(colors.hover, enter)
        assertEquals(listOf(colors.hover), enterPath.distinct())
        val (press, pressPath) = change(colors.hover) { compose.onNodeWithTag(CONTROL).performMouseInput { press() } }
        assertEquals(colors.pressed, press)
        assertEquals(listOf(colors.pressed), pressPath.distinct())
        compose.onNodeWithTag(CONTROL).performMouseInput { release() }
        compose.mainClock.advanceTimeBy(400)
        val (exit, exitPath) = change(colors.hover) { hoverOut() }
        assertEquals(Color.Transparent, exit)
        assertEquals(listOf(Color.Transparent), exitPath.distinct())
    }

    @Test
    fun withAMouseASelectionStillEasesAndFadesOnlyItsOpacity() {
        control(InputMode.Pointer)
        val (first, path) = change(Color.Transparent) { selected = true }
        assertTrue("the selection fades in, its first frame was $first", first != colors.selection)
        assertEquals(colors.selection, path.last())
        val (_, out) = change(colors.selection) { selected = false }
        assertEquals(Color.Transparent, out.last())
        // on the way only the opacity drops: no grey (the clear black it ends on is the target itself)
        out.filter { it != Color.Transparent }.forEach { assertSameHue(colors.selection, it) }
    }

    @Test
    fun byTouchAPressEasesInWithoutADarkFrame() {
        control(InputMode.Touch)
        val (first, path) = change(Color.Transparent) { compose.onNodeWithTag(CONTROL).performTouchInput { down(center) } }
        assertTrue("the press fades in by touch, its first frame was $first", first != colors.pressed)
        assertTrue("frames between clear and pressed: $path", path.size > 2)
        assertEquals(colors.pressed, path.last())
        path.forEach { assertSameHue(colors.pressed, it) }
    }

    private fun assertSameHue(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.02f && abs(expected.green - actual.green) < 0.02f && abs(expected.blue - actual.blue) < 0.02f
        assertTrue("$actual should be $expected with another opacity", close)
    }

    // ------------------------------------------------------------------ press (Desktop Kit 2.3)

    private var pressed by mutableStateOf(false)

    private fun pressable(tier: MotionTier) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    Box(Modifier.padding(20.dp).pressScale(pressed).size(100.dp).background(Color.Red).testTag(CONTROL))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun width(): Float = compose.onNodeWithTag(CONTROL).getBoundsInRoot().let { (it.right - it.left).value }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** 1.0.8 (Desktop Kit 2.8.0): letting go no longer bounces a touch over the rest. */
    @Test
    fun aPhonesPressIsLighterAndComesBackWithoutABounce() {
        pressable(MotionTier.Gesture)
        compose.runOnIdle { pressed = true }
        repeat(30) { frame() }
        assertEquals("three quarters of the 0.96 shrink", 97f, width(), 1f)
        compose.runOnIdle { pressed = false }
        var widest = 0f
        repeat(40) {
            frame()
            widest = maxOf(widest, width())
        }
        assertTrue("letting go passed the rest size: $widest", widest <= 100.01f)
        assertEquals(100f, width(), 0.5f)
    }

    @Test
    fun aTabletsPressIsAsBefore() {
        pressable(MotionTier.Timed)
        compose.runOnIdle { pressed = true }
        repeat(30) { frame() }
        assertEquals(96f, width(), 1f)
        compose.runOnIdle { pressed = false }
        var widest = 0f
        repeat(40) {
            frame()
            widest = maxOf(widest, width())
        }
        assertTrue("no bounce wider up: $widest", widest <= 100.01f)
    }

    // ------------------------------------------------------------------ haptics (Desktop Kit 2.0)

    private val felt = mutableListOf<HapticFeedbackType>()

    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            felt += hapticFeedbackType
        }
    }

    private var on by mutableStateOf(false)
    private var chosen by mutableStateOf(1)

    private fun controls(mode: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = mode) {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    Column {
                        NagSwitch(on, { on = it }, "Otomatik devam et")
                        NagCheckbox(on, { on = it }, "Bu dosya için kaydet")
                        FilterChip("24 bit", on, { on = it })
                        SegmentedChoice(listOf(1 to "Açık", 2 to "Koyu"), chosen, { chosen = it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun byTouchTheTogglesAndTheSegmentsVibrate() {
        controls(InputMode.Touch)
        compose.onNodeWithText("Otomatik devam et").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bu dosya için kaydet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("24 bit").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Koyu").performClick()
        compose.waitForIdle()
        // the one chosen already: nothing
        compose.onNodeWithText("Koyu").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff, HapticFeedbackType.ToggleOn, HapticFeedbackType.SegmentTick),
            felt,
        )
    }

    @Test
    fun withAMouseNothingVibrates() {
        controls(InputMode.Pointer)
        compose.onNodeWithText("Otomatik devam et").performClick()
        compose.onNodeWithText("Koyu").performClick()
        compose.waitForIdle()
        assertTrue(on)
        assertEquals(emptyList<HapticFeedbackType>(), felt)
    }

    // ------------------------------------------------------------------ undo bars (Desktop Kit 2.0, 2.3)

    private fun undoBars(tier: MotionTier, state: UndoBarState) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        UndoBarHost(state, Modifier.width(360.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun onAPhoneASwipedUndoBarCarriesOnBeforeItGoes() {
        val state = UndoBarState()
        var undone = 0
        state.show("Uzun bir iletinin tamamı: on iki parça sıradan kaldırıldı", "Geri al") { undone++ }
        undoBars(MotionTier.Gesture, state)
        compose.mainClock.autoAdvance = false
        val bar = compose.onNodeWithText("Uzun bir iletinin tamamı: on iki parça sıradan kaldırıldı")
        val start = bar.getBoundsInRoot().left
        bar.performTouchInput { swipe(center, center + Offset(-150f, 0f), durationMillis = 60) }
        compose.waitForIdle()
        assertEquals("still on its way out", 1, state.messages.size)
        var frames = 0
        var last = bar.getBoundsInRoot().left
        var movedOn = false
        while (state.messages.isNotEmpty() && frames < 90) {
            frame()
            frames++
            if (state.messages.isNotEmpty()) {
                val left = bar.getBoundsInRoot().left
                if (left < last) movedOn = true
                last = left
            }
        }
        assertTrue("it carried on after the finger let go", movedOn)
        assertTrue("it went within 90 frames ($frames)", state.messages.isEmpty())
        assertTrue("it went toward the start: $start → $last", last < start)
        assertEquals("a swipe does not take the change back", 0, undone)
    }

    @Test
    fun aShortSwipeLeavesTheBar() {
        val state = UndoBarState()
        state.show("3 parça sıradan kaldırıldı", "Geri al") {}
        undoBars(MotionTier.Gesture, state)
        compose.onNodeWithText("3 parça sıradan kaldırıldı").performTouchInput { swipe(center, center + Offset(-30f, 0f), durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(1, state.messages.size)
    }

    // ------------------------------------------------------------------ names for TalkBack (Desktop Kit 2.0)

    @Test
    fun theSliderSaysItsNameAndValue() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                NagSlider(value = 0.4f, onValueChange = {}, label = "Ses düzeyi", valueText = { "%" + (it * 100).toInt() })
            }
        }
        compose.onNodeWithContentDescription("Ses düzeyi").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "%40"))
    }

    @Test
    fun aFieldSaysItsErrorAndClears() {
        var text by mutableStateOf("70000")
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                NagTextField(text, { text = it }, "Bağlantı noktası", clearable = true, error = true, supportingText = "1 ile 65535 arasında bir sayı")
            }
        }
        compose.onNodeWithContentDescription("Bağlantı noktası")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "1 ile 65535 arasında bir sayı"))
        compose.onNodeWithText("1 ile 65535 arasında bir sayı").assertExists()
        compose.onNodeWithContentDescription("Metni temizle").performClick()
        compose.waitForIdle()
        assertEquals("", text)
        compose.onNodeWithContentDescription("Metni temizle").assertDoesNotExist()
    }

    private companion object {
        const val CONTROL = "control"
    }
}
