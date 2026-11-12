package app.nagmedar.ui.podcast

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.BackButton
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.gestureNavigation
import app.nagmedar.ui.shell.holdBackButton
import app.nagmedar.ui.shell.releaseBackButton
import app.nagmedar.ui.theme.InputMode
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
 * 1.0.4, the owner's report from his phone (Galaxy S24, Android 17, three buttons): "Podcast sayfasında podcast
 * bölümüne tıklayıp geri gelince animasyon eskisi gibi". The Podcast page in the phone's shell as the app has it: a
 * show's page open, an episode's page over it; the Back button pressed as Android 16 and later send it ([BackButton]:
 * a predictive Back with no edge whose progress the system runs by itself while the button is down). 1.0.3 took
 * that progress for a finger's: the page shrank into a rounded card and leant aside before it left, the Back of
 * 1.0.2. Under the button a page now keeps its place and its size, and the release takes it out whole over the end
 * edge, the way its own arrow plays: the episode's page first, then the show's. A finger's Back from the edge
 * still shrinks it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class EpisodeBackButtonTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var model by mutableStateOf(PodcastSamples.model(page = PodcastSamples.page))

    private val actions = PodcastActions(
        onClosePage = {
            calls += "close-page"
            model = model.copy(page = null)
        },
        onOpenEpisode = { id ->
            calls += "open-episode"
            model = model.copy(episodeId = id, episode = PodcastSamples.episodePage())
        },
        onCloseEpisode = {
            calls += "close-episode"
            model = model.copy(episodeId = null, episode = null)
        },
    )

    /** The phone's shell on its Podcast tab, the sample show's page open. */
    private fun phone() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                TestShell(WidthClass.Compact, startDestination = Destination.Podcasts) { _, _ -> PodcastsContent(model, actions) }
            }
        }
        compose.waitForIdle()
    }

    private fun there(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Where [tag] is drawn in the window: its layers' slide, scale and lean counted in, and not cut at the window's edge. */
    private fun drawn(tag: String): Rect {
        val coordinates = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
    }

    private fun assertInPlace(message: String, rest: Rect, now: Rect) {
        val apart = maxOf(abs(rest.left - now.left), abs(rest.top - now.top), abs(rest.right - now.right), abs(rest.bottom - now.bottom))
        assertTrue("$message: at rest $rest, now $now", apart <= 0.5f)
    }

    /**
     * The Back button on the page [tag]: nothing of it moves while the button is down; let go, it leaves as tall and
     * as wide as it was, over the end edge, and is gone.
     */
    private fun pressBackOn(tag: String) {
        val rest = drawn(tag)
        assertTrue("the page fills the content's width: $rest", rest.width > 0f && rest.height > 0f)
        val before = calls.toList()
        compose.mainClock.autoAdvance = false
        compose.holdBackButton(frames = 10) { frame -> assertInPlace("$tag, frame $frame: the page moves under the Back button", rest, drawn(tag)) }
        assertEquals("the button down closes nothing", before, calls)
        compose.releaseBackButton()
        var onItsWay = 0
        var left = rest.left
        repeat(45) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (!there(tag)) return@repeat
            val now = drawn(tag)
            assertEquals("$tag, frame $frame after the release: the page leaves as tall as it was ($rest, now $now)", rest.height, now.height, 0.5f)
            assertEquals("$tag, frame $frame after the release: and as wide ($rest, now $now)", rest.width, now.width, 0.5f)
            assertEquals("$tag, frame $frame after the release: on its own line ($rest, now $now)", rest.top, now.top, 0.5f)
            // (the spring's last pixel settles from just beyond its end)
            assertTrue("$tag, frame $frame after the release: towards the end edge, never back ($left, now $now)", now.left >= left - 2f)
            if (now.left > rest.left + 1f && now.left < rest.right) onItsWay++
            left = now.left
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("$tag: the way out was seen in $onItsWay frames", onItsWay >= 3)
        assertTrue("$tag is gone", !there(tag))
    }

    @Test
    fun theBackButtonTakesTheEpisodesPageOutWholeThenTheShows() {
        phone()
        compose.onAllNodesWithTag("podcast-episode")[0].performClick()
        compose.waitForIdle()
        assertEquals(listOf("open-episode"), calls)
        assertTrue("the episode's page is open", there("episode-detail"))

        pressBackOn("episode-detail")
        assertEquals("Back closed the episode's page, and only it", listOf("open-episode", "close-episode"), calls)
        assertTrue("the show's page is there again", there("podcast-page"))

        pressBackOn("podcast-page")
        assertEquals("the next Back closed the show's page", listOf("open-episode", "close-episode", "close-page"), calls)
    }

    /**
     * What tells the test above from an idle one: with gesture navigation a finger's Back from the left edge, at the
     * button's progress, does shrink the page.
     */
    @Test
    fun aFingersBackStillShrinksTheEpisodesPage() {
        phone()
        compose.gestureNavigation()
        compose.onAllNodesWithTag("podcast-episode")[0].performClick()
        compose.waitForIdle()
        val rest = drawn("episode-detail")
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun finger(progress: Float) = BackEventCompat(40f, 600f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(finger(0f)) }
        for (frame in 1..10) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(finger(BackButton.progressAtFrame(frame))) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        val shrunk = drawn("episode-detail")
        assertTrue("the finger shrinks the page: at rest $rest, now $shrunk", shrunk.height < rest.height * 0.97f && shrunk.width < rest.width * 0.97f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertInPlace("given up, the page is back", rest, drawn("episode-detail"))
        assertEquals(listOf("open-episode"), calls)
    }

    /**
     * The owner's phone has three buttons: its window keeps no edge for a Back swipe, so nothing there is a finger's.
     * Should its button name an edge for the progress it runs (not every system says "no edge"), the page stays
     * where it is all the same, and the release closes it.
     */
    @Test
    fun withThreeButtonsABackThatNamesAnEdgeLeavesThePageInPlaceToo() {
        phone()
        compose.onAllNodesWithTag("podcast-episode")[0].performClick()
        compose.waitForIdle()
        val rest = drawn("episode-detail")
        val dispatcher = compose.activity.onBackPressedDispatcher
        fun edged(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(edged(0f)) }
        for (frame in 1..10) {
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(edged(BackButton.progressAtFrame(frame))) }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertInPlace("frame $frame: the page moves under a Back that names an edge", rest, drawn("episode-detail"))
        }
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf("open-episode", "close-episode"), calls)
        assertTrue("the episode's page is gone", !there("episode-detail"))
    }
}
