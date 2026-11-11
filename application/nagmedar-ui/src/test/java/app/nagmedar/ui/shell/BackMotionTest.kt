package app.nagmedar.ui.shell

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 1.0.3: what Back plays in the shell (the user, 12 Nov: "Geri tuşundaki efekti hala beğenmiyorum"; his phone has
 * three-button navigation). Looked at frame by frame in the real shell, the pages pure red and pure blue
 * ([ExposureFrame]), a green mark in the middle of the red ones telling where they are:
 *
 * - Back from another tab's first page, and the Müzik tab chosen, fade through as a tab chosen does: the two tabs
 *   never share the window (they slid side by side as a page's Back does, two lists on one colour with no edge
 *   between them). So does the Back button's predictive Back of Android 16 and later (no edge), on a phone with
 *   gesture navigation too, where a swipe keeps its own way.
 * - A page opened from a page comes in opaque over it; the page under neither travels nor fades: it sinks in its
 *   place and darkens under a scrim (Desktop Kit 2.4.0). Back is the same way back, by the button, by Android 16's
 *   button and sought by the gesture. On the rail as on the phone.
 * - With reduced motion everything is there at once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class BackMotionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val camera by lazy { ExposureCamera(compose) }

    private lateinit var view: View
    private var navigator: ShellNavigator? = null

    /** The real shell: Müzik and "Daha fazla" red with a green mark in their middle, every other page blue. */
    private fun window(widthClass: WidthClass = WidthClass.Compact, reduced: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            view = LocalView.current
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch, reduceMotion = reduced) {
                TestShell(widthClass, chromeGlass = ChromeGlass.Solid, screen = { destination, nav ->
                    navigator = nav
                    val red = destination == Destination.Music || destination == Destination.More
                    Box(
                        Modifier.fillMaxSize().background(if (red) Color.Red else Color.Blue).testTag("screen-${destination.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (red) Box(Modifier.size(24.dp, 160.dp).background(Color.Green))
                    }
                })
            }
        }
        compose.waitForIdle()
    }

    /** What a phone with gesture navigation sends: its side edges take the Back gesture. */
    private fun gestureNavigation() {
        val edge = with(compose.density) { 24.dp.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(edge, 0, edge, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    private fun go(destination: Destination, tab: Boolean) {
        compose.runOnIdle { if (tab) navigator?.selectTab(destination) else navigator?.open(destination) }
        compose.waitForIdle()
    }

    private val dispatcher get() = compose.activity.onBackPressedDispatcher

    private fun back(progress: Float, edge: Int) = BackEventCompat(40f, 400f, progress, edge)

    private fun width(tag: String): Float = compose.onNodeWithTag(tag).getBoundsInRoot().let { (it.right - it.left).value }

    /** The frames of [frames] more of the clock after [change]. */
    private fun play(label: String, frames: Int = 40, change: () -> Unit): List<ExposureFrame> {
        val taken = mutableListOf<ExposureFrame>()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { change() }
        repeat(frames) { camera.step("$label, frame $it", taken) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return taken
    }

    /** At most this many pixels more than at rest may be blended: the edges of a scaled page, the bars' words changing. */
    private fun tolerance(frame: ExposureFrame) = frame.total / 100

    /** A good part of the window is the blue page's, beyond what the bars have of that colour ([rest]: the window with no blue page). */
    private fun ExposureFrame.showsBlue(rest: ExposureFrame) = blue - rest.blue > total / 10

    private fun ExposureFrame.showsRed(rest: ExposureFrame) = red - rest.red > total / 10

    /** No frame draws one page through the other, and neither page is part transparent ([a], [b]: the window at rest before and after). */
    private fun assertBothOpaque(frames: List<ExposureFrame>, a: ExposureFrame, b: ExposureFrame) {
        for (f in frames) {
            assertTrue("one page is seen through the other: $f (at rest $a, $b)", f.mixed <= maxOf(a.mixed, b.mixed) + tolerance(f))
            assertTrue("the page on top is part transparent: $f (at rest $a, $b)", f.fadedBlue <= maxOf(a.fadedBlue, b.fadedBlue) + tolerance(f))
            assertTrue("the page under is faded: $f (at rest $a, $b)", f.fadedRed <= maxOf(a.fadedRed, b.fadedRed) + tolerance(f))
        }
    }

    /**
     * A page moving over the red one: in the frames that show both, the red page is in its place (its mark in the
     * middle: it travelled a quarter of the width before) and dark under the scrim ([redRest]: with no blue page).
     */
    private fun assertThePageUnderSinksInItsPlace(frames: List<ExposureFrame>, redRest: ExposureFrame, blueRest: ExposureFrame) {
        val both = frames.filter { it.showsBlue(redRest) && it.showsRed(blueRest) }
        assertTrue("no frame shows both pages: ${frames.joinToString("\n")}", both.isNotEmpty())
        // (the mark whole, or nearly: what is left of a mark the page on top half covers has its middle elsewhere)
        val marked = both.filter { it.greenAt != null && it.green >= redRest.green * 8 / 10 }
        assertTrue("the page under is never seen with its mark: ${both.joinToString("\n")}", marked.isNotEmpty())
        for (f in marked) assertTrue("the page under travels: $f", abs(f.greenAt!! - redRest.greenAt!!) < 0.02f)
        for (f in both) assertTrue("the page under does not darken: $f (at rest ${redRest.redLevel})", f.redLevel < redRest.redLevel - 5)
    }

    /** The two tabs never share the window: the one is gone before the other appears. */
    private fun assertFadeThrough(frames: List<ExposureFrame>, redRest: ExposureFrame, blueRest: ExposureFrame) {
        for (f in frames) {
            assertFalse("the two tabs share the window: $f (at rest $redRest, $blueRest)", f.showsBlue(redRest) && f.showsRed(blueRest))
            assertTrue("one tab is seen through the other: $f", f.mixed <= maxOf(redRest.mixed, blueRest.mixed) + tolerance(f))
        }
    }

    // ------------------------------------------------------------------ the tabs

    /** The user's phone: three-button navigation's Back from another tab goes to Müzik as a tab chosen does. */
    @Test
    fun backFromAnotherTabFadesThroughToMuzik() {
        window()
        val music = camera.frame("Müzik at rest")
        go(Destination.Podcasts, tab = true)
        val podcast = camera.frame("Podcast at rest")
        assertTrue("Podcast is the blue page: $podcast", podcast.showsBlue(music))
        val frames = play("Back") { dispatcher.onBackPressed() }
        assertFadeThrough(frames, music, podcast)
        compose.onNodeWithTag("screen-Podcasts").assertDoesNotExist()
        assertTrue("Müzik is back: ${frames.last()}", frames.last().showsRed(podcast))
        assertEquals("as bright as it was: ${frames.last()}", music.redLevel, frames.last().redLevel)
    }

    /** Android 16 and later start a predictive Back for the button too (no edge); with three buttons the shell's Back still takes it whole. */
    @Test
    fun theBackButtonsPredictiveBackFadesThroughToo() {
        window()
        val music = camera.frame("Müzik at rest")
        go(Destination.Podcasts, tab = true)
        val podcast = camera.frame("Podcast at rest")
        val full = width("screen-Podcasts")
        val frames = mutableListOf<ExposureFrame>()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_NONE)) }
        repeat(3) { camera.step("the button down, frame $it", frames) }
        assertEquals("nothing moves while the button is down", full, width("screen-Podcasts"), 0.5f)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        repeat(40) { camera.step("the button let go, frame $it", frames) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFadeThrough(frames, music, podcast)
        compose.onNodeWithTag("screen-Podcasts").assertDoesNotExist()
        assertTrue("Müzik is back: ${frames.last()}", frames.last().showsRed(podcast))
    }

    /** A phone with gesture navigation: a hardware Back key (and Android 16's button) is no swipe; navigation plays the tabs' fade-through for it. */
    @Test
    fun withGestureNavigationTheBackKeyFadesThroughAndDoesNotShrinkTheTab() {
        window()
        val music = camera.frame("Müzik at rest")
        gestureNavigation()
        go(Destination.Podcasts, tab = true)
        val podcast = camera.frame("Podcast at rest")
        val full = width("screen-Podcasts")
        val frames = mutableListOf<ExposureFrame>()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_NONE)) }
        repeat(3) {
            camera.step("the key down, frame $it", frames)
            // the swipe's way (the tab shrinking toward 90 %, leaning) is not the key's
            assertEquals("the tab does not shrink under a key", full, width("screen-Podcasts"), 0.5f)
        }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        repeat(40) { camera.step("the key let go, frame $it", frames) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFadeThrough(frames, music, podcast)
        compose.onNodeWithTag("screen-Podcasts").assertDoesNotExist()
        assertTrue("Müzik is back: ${frames.last()}", frames.last().showsRed(podcast))
    }

    /** The Müzik tab chosen from another tab is a pop as well: it fades through like any tab. */
    @Test
    fun theMuzikTabChosenFadesThrough() {
        window()
        val music = camera.frame("Müzik at rest")
        go(Destination.Podcasts, tab = true)
        val podcast = camera.frame("Podcast at rest")
        val frames = play("the Müzik tab") { navigator?.selectTab(Destination.Music) }
        assertFadeThrough(frames, music, podcast)
        assertTrue("Müzik is back: ${frames.last()}", frames.last().showsRed(podcast))
    }

    // ------------------------------------------------------------------ the pages

    @Test
    fun aPageComesInOpaqueOverThePageThatSinksAndDarkensInItsPlace() {
        window()
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        assertTrue("the page under has its mark in the middle: $more", more.greenAt != null && abs(more.greenAt!! - 0.5f) < 0.02f)
        val opening = play("the page opened") { navigator?.open(Destination.Equalizer) }
        val page = camera.frame("the page at rest")
        assertTrue("the page is there: $page", page.showsBlue(more))
        assertBothOpaque(opening, more, page)
        assertThePageUnderSinksInItsPlace(opening, more, page)
        // covered, the page under is gone
        compose.onNodeWithTag("screen-More").assertDoesNotExist()
    }

    @Test
    fun backFromAPageSlidesItAwayOverThePageRisingInItsPlace() {
        window()
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        go(Destination.Equalizer, tab = false)
        val page = camera.frame("the page at rest")
        val closing = play("Back") { dispatcher.onBackPressed() }
        assertBothOpaque(closing, more, page)
        assertThePageUnderSinksInItsPlace(closing, more, page)
        compose.onNodeWithTag("screen-Equalizer").assertDoesNotExist()
        val end = closing.last()
        assertFalse("the page is gone: $end", end.showsBlue(more))
        assertEquals("the page under is as bright as it was: $end", more.redLevel, end.redLevel)
        assertEquals("and where it was: $end", more.greenAt!!, end.greenAt!!, 0.005f)
    }

    /** Android 16 and later: the Back button starts a predictive Back with no edge, played through on the release: the same way out. */
    @Test
    fun theBackButtonsPredictiveBackPlaysThePagesWayOut() {
        window()
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        go(Destination.Equalizer, tab = false)
        val page = camera.frame("the page at rest")
        val frames = mutableListOf<ExposureFrame>()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_NONE)) }
        repeat(3) { camera.step("the button down, frame $it", frames) }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        repeat(40) { camera.step("the button let go, frame $it", frames) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertBothOpaque(frames, more, page)
        assertThePageUnderSinksInItsPlace(frames, more, page)
        compose.onNodeWithTag("screen-Equalizer").assertDoesNotExist()
        assertEquals("the page under is as bright as it was: ${frames.last()}", more.redLevel, frames.last().redLevel)
    }

    /** The Back gesture seeks the same way out with the finger; given up, the page is back; let go, it leaves. */
    @Test
    fun theBackGestureSeeksThePagesWayOut() {
        window()
        gestureNavigation()
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        go(Destination.Equalizer, tab = false)
        val page = camera.frame("the page at rest")
        val frames = mutableListOf<ExposureFrame>()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_LEFT)) }
        for (p in listOf(0.1f, 0.25f, 0.4f, 0.55f)) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(back(p, BackEventCompat.EDGE_LEFT)) }
            camera.step("finger at $p", frames)
            camera.step("finger at $p, a frame on", frames)
        }
        assertBothOpaque(frames, more, page)
        assertThePageUnderSinksInItsPlace(frames, more, page)
        // given up: the page covers its place again
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        val givenUp = mutableListOf<ExposureFrame>()
        repeat(40) { camera.step("given up, frame $it", givenUp) }
        assertBothOpaque(givenUp, more, page)
        compose.onNodeWithTag("screen-Equalizer").assertExists()
        assertFalse("the page under is covered again: ${givenUp.last()}", givenUp.last().showsRed(page))
        // a whole gesture leaves the page
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(back(0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(back(0.5f, BackEventCompat.EDGE_LEFT))
        }
        val out = mutableListOf<ExposureFrame>()
        camera.step("finger at 0.5", out)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        repeat(40) { camera.step("let go, frame $it", out) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertBothOpaque(out, more, page)
        compose.onNodeWithTag("screen-Equalizer").assertDoesNotExist()
        assertEquals("the page under is as bright as it was: ${out.last()}", more.redLevel, out.last().redLevel)
    }

    /** A tablet's rail has the same pages: the same way, on its timed curve. */
    @Test
    @Config(qualifiers = "tr-w800dp-h1280dp-mdpi")
    fun onTheRailAPageMovesTheSameWay() {
        window(WidthClass.Medium)
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        val opening = play("the page opened") { navigator?.open(Destination.Equalizer) }
        val page = camera.frame("the page at rest")
        assertBothOpaque(opening, more, page)
        assertThePageUnderSinksInItsPlace(opening, more, page)
        val closing = play("Back") { dispatcher.onBackPressed() }
        assertBothOpaque(closing, more, page)
        assertThePageUnderSinksInItsPlace(closing, more, page)
        assertEquals("the page under is as bright as it was: ${closing.last()}", more.redLevel, closing.last().redLevel)
    }

    @Test
    fun withReducedMotionPagesAndTabsAreThereAtOnce() {
        window(reduced = true)
        val music = camera.frame("Müzik at rest")
        go(Destination.More, tab = true)
        val more = camera.frame("Daha fazla at rest")
        val opening = play("the page opened", frames = 6) { navigator?.open(Destination.Equalizer) }
        val page = camera.frame("the page at rest")
        // from the first frame on the page is there, whole; the page under is never seen dark or beside it
        for (f in opening) {
            assertTrue("the page is there at once: $f", f.showsBlue(more))
            assertFalse("the page under is still seen: $f", f.showsRed(page))
        }
        val closing = play("Back", frames = 6) { dispatcher.onBackPressed() }
        for (f in closing) {
            assertFalse("the page is still seen: $f", f.showsBlue(more))
            assertEquals("the page under is as bright as it was: $f", more.redLevel, f.redLevel)
        }
        // and the tabs: Back from "Daha fazla" is at Müzik at once
        val tabs = play("Back to Müzik", frames = 6) { dispatcher.onBackPressed() }
        for (f in tabs) assertEquals("Müzik as bright as it was: $f", music.redLevel, f.redLevel)
        compose.onNodeWithTag("screen-Music").assertExists()
    }

    // ------------------------------------------------------------------ the rules

    @Test
    fun aPopBetweenTabsIsNotAPagesBack() {
        // Back from another tab's first page, whoever pops it
        assertTrue(NavRules.tabPop(Destination.Podcasts, Destination.Music, NavKind.BottomBar, move = null))
        assertTrue(NavRules.tabPop(Destination.More, Destination.Music, NavKind.BottomBar, move = NavMove.Tab))
        assertTrue(NavRules.tabPop(Destination.Radio, Destination.Music, NavKind.Rail, move = null))
        // the Müzik tab chosen from a page of another tab, a tab's first page again
        assertTrue(NavRules.tabPop(Destination.Device, Destination.Music, NavKind.BottomBar, move = NavMove.Tab))
        assertTrue(NavRules.tabPop(Destination.Equalizer, Destination.More, NavKind.BottomBar, move = NavMove.Reselect))
        // a page's Back: the system's (no move of the navigator's), the top bar's arrow, a page gone back to
        assertFalse(NavRules.tabPop(Destination.Settings, Destination.More, NavKind.BottomBar, move = null))
        assertFalse(NavRules.tabPop(Destination.Equalizer, Destination.Music, NavKind.BottomBar, move = null))
        assertFalse(NavRules.tabPop(Destination.Settings, Destination.More, NavKind.BottomBar, move = NavMove.Back))
        assertFalse(NavRules.tabPop(Destination.About, Destination.Settings, NavKind.Rail, move = NavMove.Back))
    }

    @Test
    fun thePageUnderSinksToNinetyFiveUnderSeventyPercentOfTheScrim() {
        assertEquals(0.95f, NagMotion.PAGE_UNDER_SCALE, 0.0001f)
        assertEquals(0.7f, NagMotion.PAGE_SCRIM, 0.0001f)
        // what the pages are told: one lies under, or a tab is left by the gesture, never both
        val pages = ShellPages()
        assertNull(pages.under)
        assertNull(pages.leaving)
        pages.over("a")
        assertEquals("a", pages.under)
        pages.gesture("b")
        assertNull(pages.under)
        assertEquals("b", pages.leaving)
        pages.over("c")
        assertNull(pages.leaving)
        pages.tabs()
        assertNull(pages.under)
        assertNull(pages.leaving)
    }
}
