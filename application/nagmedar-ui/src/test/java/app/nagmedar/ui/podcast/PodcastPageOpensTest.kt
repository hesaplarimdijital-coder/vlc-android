package app.nagmedar.ui.podcast

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
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
 * 1.0.5, the owner's report from his phone (Galaxy S24, 1080 × 2340 at 480 dpi; 12 Nov, with a screen recording):
 * "Podcastte sayfası böyle oluyor." He taps his show's row on the Podcasts, its page slides in, and in the frame the
 * opening ends everything of the page is gone; only Back brings the list again. It was so since 1.0.3, whose
 * shared elements (the row's cover flies to the page's) came with it.
 *
 * The Podcasts in the phone's shell as the app has them, in a window of his phone's size: nothing open, a show's row
 * tapped. Its page must be in its place when all has landed, scroll, let an episode's page over it come and go,
 * leave on Back, and open again. A row far down the list is the one that lost its page: its cover has the longest
 * way to the page's, and lands after the page itself ([app.nagmedar.ui.shell.DetailDepthTest] has the mechanism
 * alone). The tests of 1.0.3 and 1.0.4 began with the page already open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w360dp-h780dp-xxhdpi")
class PodcastPageOpensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var model by mutableStateOf(PodcastSamples.model())

    /** A show's page as long as the owner's: forty episodes. */
    private fun page(feedId: Long) = PodcastPage(
        PodcastSamples.summaries.first { it.feed.id == feedId },
        List(40) { PodcastSamples.episodes[it % PodcastSamples.episodes.size].let { e -> e.copy(id = 100L + it, feedId = feedId, guid = "bölüm-$it") } },
    )

    private val actions = PodcastActions(
        onOpen = { id ->
            calls += "open"
            model = model.copy(page = page(id))
        },
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

    /** The phone's shell on its Podcasts tab, the sample shows followed, none open. */
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

    /** Where the first [tag] is drawn in the window: its layers' slide counted in, and not cut at the window's edge. */
    private fun drawn(tag: String): Rect {
        val coordinates = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().layoutInfo.coordinates
        return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
    }

    /** The window's width in pixels (the phone's content is as wide). */
    private fun width(): Float = compose.onAllNodesWithTag("podcast-page", useUnmergedTree = true).fetchSemanticsNodes().first()
        .layoutInfo.coordinates.findRootCoordinates().size.width.toFloat()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** The show's page fills the content's width from its start edge, and its episodes are there. */
    private fun assertThePageIsInItsPlace(message: String) {
        assertTrue("$message: the show's page is there", there("podcast-page"))
        val page = drawn("podcast-page")
        val width = width()
        assertTrue(
            "$message: the page is in its place, from edge to edge of a window $width px wide, not beyond it: $page",
            abs(page.left) <= 0.5f && abs(page.right - width) <= 0.5f,
        )
        assertTrue("$message: its episodes are there", there("podcast-episode"))
        val episode = drawn("podcast-episode")
        assertTrue("$message: an episode is drawn in the window: $episode", episode.left >= 0f && episode.right <= width + 0.5f && episode.height > 0f)
        assertTrue("$message: the list is covered", !there("podcast-row"))
    }

    /** Taps the row of the show that is [index]th in the list, and lets its opening play to the end. */
    private fun tapRow(index: Int) {
        compose.onAllNodesWithTag("podcast-row")[index].performClick()
        compose.waitForIdle()
    }

    @Test
    fun aShowsPageOpenedFromARowFarDownTheListIsThereAndStays() {
        phone()
        val rows = compose.onAllNodesWithTag("podcast-row").fetchSemanticsNodes().size
        assertTrue("the sample shows' rows fill the list: $rows", rows >= 4)
        tapRow(rows - 1)
        assertEquals(listOf("open"), calls)
        assertThePageIsInItsPlace("opened from the last row")
        // and it stays, frames on
        compose.mainClock.autoAdvance = false
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        assertThePageIsInItsPlace("twenty frames on")
    }

    @Test
    fun thePageOpenedFromItsRowScrolls() {
        phone()
        tapRow(compose.onAllNodesWithTag("podcast-row").fetchSemanticsNodes().size - 1)
        assertThePageIsInItsPlace("opened from the last row")
        val before = drawn("podcast-episode")
        compose.onNodeWithTag("podcast-page").performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        assertThePageIsInItsPlace("scrolled")
        // (the first episode there now is one further down the page, or the same one higher up)
        val after = drawn("podcast-episode")
        assertTrue("the episodes move under the finger: the first was at $before, the first is now at $after", after.top < before.top - 100f)
    }

    @Test
    fun anEpisodeComesAndGoesOverThePageAndBackLeavesItAndItOpensAgain() {
        phone()
        val last = compose.onAllNodesWithTag("podcast-row").fetchSemanticsNodes().size - 1
        tapRow(last)
        assertThePageIsInItsPlace("opened from the last row")

        compose.onAllNodesWithTag("podcast-episode")[0].performClick()
        compose.waitForIdle()
        assertTrue("the episode's page is open", there("episode-detail"))
        back()
        assertEquals(listOf("open", "open-episode", "close-episode"), calls)
        assertTrue("the episode's page is gone", !there("episode-detail"))
        assertThePageIsInItsPlace("back from the episode")

        back()
        assertEquals(listOf("open", "open-episode", "close-episode", "close-page"), calls)
        assertTrue("Back leaves the show's page", !there("podcast-page"))
        assertTrue("the list is there again", there("podcast-row"))
        val row = drawn("podcast-row")
        assertTrue("the list is in its place: $row", row.left >= 0f && row.width > 0f)

        // the second opening: from the first row (the shortest way), then from the last again
        tapRow(0)
        assertThePageIsInItsPlace("opened again, from the first row")
        back()
        assertTrue("the list is there again", there("podcast-row"))
        tapRow(last)
        assertThePageIsInItsPlace("opened a third time, from the last row")
    }
}
