package app.nagmedar.ui.shell

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.3 (Desktop Kit 2.4.0's test of the same name): no frame of a detail transition draws one screen through the
 * other, the double exposure of Desktop Kit 2.3.0's depth, where the detail's text was drawn over the half faded
 * list's. The list is pure red, the detail pure blue, the theme dark ([ExposureFrame]). Every frame of an opening,
 * a closing, the Back gesture (let go and given up) and an opening turned around is looked at; a few pixels along
 * the edges of a shrunk or sinking screen are blended by antialiasing, hence the tolerance.
 *
 * 1.0.4: the Back button is no finger ([BackButton]: Android 16 and later send it as a predictive Back with a
 * progress of the system's own). Under it the detail stays whole, on a phone and wider up, and the release plays
 * the way out the detail's own arrow plays; 1.0.3 shrank the detail into a rounded card under the button.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class DetailNoDoubleExposureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val camera by lazy { ExposureCamera(compose) }

    private var detail by mutableStateOf<String?>(null)

    private fun show(tier: MotionTier, card: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalMotionTier provides tier) {
                    // on the page's colour, as every screen of the app is (the shell's frame): it is what shows in
                    // the margins a sinking list opens and under a list that fades; the test activity's own window
                    // is white, which under a scrim reads as red and blue at once
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                        DetailTransition(
                            detail = detail,
                            onBack = { detail = null },
                            sharedElements = card,
                            list = {
                                Box(Modifier.fillMaxSize().background(Color.Red)) {
                                    if (card) Box(Modifier.detailSharedCard("card").size(160.dp).background(Color.Green))
                                }
                            },
                        ) {
                            Column(Modifier.fillMaxSize().background(Color.Blue)) {
                                if (card) Box(Modifier.detailSharedCard("card").fillMaxWidth().height(300.dp).background(Color.Green))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** At most this many pixels of a frame may be blended: the edges of a scaled screen, not a screen seen through another. */
    private fun tolerance(frame: ExposureFrame) = frame.total / 100

    private fun assertNoDoubleExposure(frames: List<ExposureFrame>) {
        for (f in frames) assertTrue("one screen is seen through the other: $f", f.mixed <= tolerance(f))
    }

    /** The phone's plain detail: both screens opaque in every frame, the detail over the list. */
    private fun assertBothOpaque(frames: List<ExposureFrame>) {
        assertNoDoubleExposure(frames)
        for (f in frames) {
            assertTrue("the detail is part transparent: $f", f.fadedBlue <= tolerance(f))
            assertTrue("the list is faded: $f", f.fadedRed <= tolerance(f))
        }
    }

    /** Some frame shows a good part of both screens (so the transition was really caught on its way). */
    private fun assertCaughtOnItsWay(frames: List<ExposureFrame>) {
        assertTrue(
            "no frame shows both screens: ${frames.joinToString("\n")}",
            frames.any { it.blue > it.total / 10 && it.red > it.total / 10 },
        )
    }

    @Test
    fun onAPhoneTheDetailSlidesInOpaqueOverTheDarkeningListAndOutAgain() {
        show(MotionTier.Gesture)
        compose.mainClock.autoAdvance = false
        val atRest = camera.frame("at rest")
        assertEquals("the list at rest is as bright as it is: $atRest", 255, atRest.redLevel)
        val opening = mutableListOf<ExposureFrame>()
        compose.runOnIdle { detail = "albüm" }
        repeat(36) { camera.step("opening frame $it", opening) }
        assertBothOpaque(opening)
        assertCaughtOnItsWay(opening)
        // the list is not faded under the detail: it darkens, more and more as the detail covers it
        val shared = opening.filter { it.blue > it.total / 10 && it.red > it.total / 10 }
        assertTrue("the list darkens under the detail: $shared", shared.all { it.redLevel < 250 })
        assertTrue("it darkens on as the detail comes: $shared", shared.zipWithNext().all { (a, b) -> b.redLevel <= a.redLevel + 1 })
        assertTrue("the detail covers the window once open: ${opening.last()}", opening.last().blue > opening.last().total * 3 / 4)

        val closing = mutableListOf<ExposureFrame>()
        compose.runOnIdle { detail = null }
        repeat(36) { camera.step("closing frame $it", closing) }
        assertBothOpaque(closing)
        assertCaughtOnItsWay(closing)
        assertEquals("the detail is gone once closed: ${closing.last()}", 0, closing.last().blue)
        assertEquals("the list is as bright as it was: ${closing.last()}", 255, closing.last().redLevel)
        compose.mainClock.autoAdvance = true
    }

    private fun back(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun onAPhoneTheBackGestureShrinksTheOpaqueDetailOverTheDimmedList() {
        detail = "albüm"
        show(MotionTier.Gesture)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.05f, 0.1f, 0.2f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            camera.step("finger at $p", frames)
            camera.step("finger at $p, a frame on", frames)
        }
        assertBothOpaque(frames)
        // the list stays dark behind the detail while the finger moves, so the shrunk detail stands out against it
        val seen = frames.filter { it.red > it.total / 100 }
        assertTrue("the list shows around the shrunk detail: ${frames.last()}", seen.isNotEmpty())
        assertTrue("the list stays dark under the finger: $seen", seen.all { it.redLevel < 200 })
        // let go: the detail slides out, still opaque, the list's scrim lightening behind it
        compose.runOnUiThread { dispatcher.onBackPressed() }
        val out = mutableListOf<ExposureFrame>()
        repeat(30) { camera.step("let go, frame $it", out) }
        assertBothOpaque(out)
        assertNull(detail)
        assertEquals("the detail is gone once closed: ${out.last()}", 0, out.last().blue)
        assertEquals("the list is as bright as it was: ${out.last()}", 255, out.last().redLevel)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun onAPhoneABackGestureGivenUpSpringsBackOpaque() {
        detail = "albüm"
        show(MotionTier.Gesture)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (p in listOf(0.2f, 0.5f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p)) }
            camera.step("finger at $p", frames)
        }
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        repeat(30) { camera.step("given up, frame $it", frames) }
        assertBothOpaque(frames)
        assertEquals("albüm", detail)
        assertTrue("the detail covers the window again: ${frames.last()}", frames.last().blue > frames.last().total * 3 / 4)
        compose.mainClock.autoAdvance = true
    }

    /**
     * 1.0.4, the owner's phone (three buttons, Android 17): "Podcast sayfasında podcast bölümüne tıklayıp geri gelince
     * animasyon eskisi gibi". The button's Back comes with a progress that runs by itself while the button is down;
     * the detail took it for a finger's and shrank, rounded and leant before it left. Now nothing moves under the
     * button, and its release slides the detail out whole and opaque, as its arrow does.
     */
    @Test
    fun onAPhoneTheBackButtonLeavesTheDetailWholeAndItsReleaseSlidesItOut() {
        detail = "albüm"
        show(MotionTier.Gesture)
        compose.mainClock.autoAdvance = false
        val open = camera.frame("open")
        assertTrue("the open detail is as tall as the window: $open", open.blueRows > 0 && open.red == 0)
        val held = mutableListOf<ExposureFrame>()
        compose.holdBackButton(frames = 10) { held += camera.frame("the button down, frame $it") }
        for (f in held) {
            assertEquals("the list shows around the detail under the button: $f", 0, f.red)
            assertEquals("the detail shrinks under the button: $f", open.blueRows, f.blueRows)
            assertEquals("the detail moves under the button: $f", open.blue, f.blue)
            assertEquals("the detail rounds its corners under the button: $f", open.mixed, f.mixed)
        }
        assertEquals("the button down closes nothing", "albüm", detail)
        compose.releaseBackButton()
        val out = mutableListOf<ExposureFrame>()
        repeat(36) { camera.step("the button let go, frame $it", out) }
        assertNull(detail)
        assertBothOpaque(out)
        assertCaughtOnItsWay(out)
        // whole on its way out: as tall as the window in every frame it is in (a finger's Back takes it out at nine tenths)
        val leaving = out.filter { it.blue > it.total / 20 }
        assertTrue("the way out was seen: ${out.joinToString("\n")}", leaving.size >= 3)
        for (f in leaving) assertEquals("the detail leaves shrunk: $f", open.blueRows, f.blueRows)
        assertEquals("the detail is gone once closed: ${out.last()}", 0, out.last().blue)
        assertEquals("the list is as bright as it was: ${out.last()}", 255, out.last().redLevel)
        compose.mainClock.autoAdvance = true
    }

    /** The same progress from a finger at the left edge does shrink it (what tells the test above from an idle one). */
    @Test
    fun onAPhoneAFingersBackAtTheButtonsProgressShrinksTheDetail() {
        detail = "albüm"
        show(MotionTier.Gesture)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        val open = camera.frame("open")
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (frame in 1..10) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(BackButton.progressAtFrame(frame))) }
            camera.step("finger, frame $frame", frames)
        }
        assertTrue("the finger shrinks the detail: ${frames.last()} (open: $open)", frames.last().blueRows < open.blueRows * 97 / 100)
        assertTrue("the list shows around it: ${frames.last()}", frames.last().red > 0)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        repeat(30) { camera.step("given up, frame $it", frames) }
        assertEquals("albüm", detail)
        compose.mainClock.autoAdvance = true
    }

    /** Back right after the tap: the opening runs back, both screens still opaque. */
    @Test
    fun onAPhoneAnOpeningTurnedAroundStaysOpaque() {
        show(MotionTier.Gesture)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnIdle { detail = "albüm" }
        repeat(6) { camera.step("opening frame $it", frames) }
        compose.runOnIdle { detail = null }
        repeat(36) { camera.step("back frame $it", frames) }
        assertBothOpaque(frames)
        assertCaughtOnItsWay(frames)
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        assertEquals("the list is as bright as it was: ${frames.last()}", 255, frames.last().redLevel)
        compose.mainClock.autoAdvance = true
    }

    /** With a card the rest of the two screens fades through: the list is gone before the detail appears, and back. */
    @Test
    fun onAPhoneWithACardTheScreensFadeThroughNotOverEachOther() {
        show(MotionTier.Gesture, card = true)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnIdle { detail = "program" }
        repeat(40) { camera.step("opening frame $it", frames) }
        assertTrue("the detail is open: ${frames.last()}", frames.last().blue > frames.last().total / 2)
        compose.runOnIdle { detail = null }
        repeat(40) { camera.step("closing frame $it", frames) }
        assertNoDoubleExposure(frames)
        // never a good part of both at once: the one is gone before the other appears
        assertTrue(
            "the two screens show together: ${frames.joinToString("\n")}",
            frames.none { it.blue > it.total / 10 && it.red > it.total / 10 },
        )
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        compose.mainClock.autoAdvance = true
    }

    /**
     * 1.0.4: nor wider up. A tablet's taskbar has the same Back button: the detail does not shrink and lean under it
     * (it did, as under a finger), and its release closes it, the fades in turn.
     */
    @Test
    fun widerUpTheBackButtonLeavesTheDetailWhole() {
        detail = "albüm"
        show(MotionTier.Timed)
        compose.mainClock.autoAdvance = false
        val open = camera.frame("open")
        assertTrue("the open detail covers the window: $open", open.blue > open.total * 3 / 4)
        val held = mutableListOf<ExposureFrame>()
        compose.holdBackButton(frames = 10) { held += camera.frame("the button down, frame $it") }
        for (f in held) {
            assertEquals("the detail shrinks under the button: $f", open.blueRows, f.blueRows)
            assertEquals("the detail moves under the button: $f", open.blue, f.blue)
        }
        assertEquals("albüm", detail)
        compose.releaseBackButton()
        val out = mutableListOf<ExposureFrame>()
        repeat(30) { camera.step("the button let go, frame $it", out) }
        assertNull(detail)
        assertNoDoubleExposure(out)
        assertEquals("the detail is gone: ${out.last()}", 0, out.last().blue)
        compose.mainClock.autoAdvance = true
    }

    /** Tablets and DeX: the shared axis's fades in turn, the list gone before the detail fades in, and back. */
    @Test
    fun widerUpTheSharedAxisFadesInTurn() {
        show(MotionTier.Timed)
        compose.mainClock.autoAdvance = false
        val frames = mutableListOf<ExposureFrame>()
        compose.runOnIdle { detail = "albüm" }
        repeat(30) { camera.step("opening frame $it", frames) }
        assertTrue("the detail is open: ${frames.last()}", frames.last().blue > frames.last().total * 3 / 4)
        compose.runOnIdle { detail = null }
        repeat(30) { camera.step("closing frame $it", frames) }
        assertNoDoubleExposure(frames)
        assertTrue(
            "the two screens show together: ${frames.joinToString("\n")}",
            frames.none { it.blue > it.total / 10 && it.red > it.total / 10 },
        )
        assertEquals("the detail is gone: ${frames.last()}", 0, frames.last().blue)
        compose.mainClock.autoAdvance = true
    }
}
