package app.nagmedar.ui.podcast

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.8 (Desktop Kit 2.8.0's pull to refresh): on a phone a finger pulling the subscriptions down reads every one
 * again, one pulling a show's page down reads that show again; the disc turns while they are read.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class PodcastPullTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val actions = PodcastActions(onRefresh = { calls += "refresh" }, onRefreshShow = { calls += "refresh:$it" })

    private fun show(model: PodcastScreenModel) {
        compose.setContent { NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) { PodcastsContent(model, actions) } }
        compose.waitForIdle()
    }

    @Test
    fun aPullOnTheSubscriptionsReadsThemAllAgain() {
        show(PodcastSamples.model())
        compose.onNode(hasTestTag("podcast-list") or hasTestTag("podcast-grid"))
            .performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun aPullOnAShowsPageReadsTheShowAgain() {
        show(PodcastSamples.model(page = PodcastSamples.page))
        compose.onNodeWithTag("podcast-page").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("refresh:${PodcastSamples.gece.id}"), calls)
    }

    @Test
    fun theDiscTurnsWhileTheShowIsRead() {
        show(PodcastSamples.model(page = PodcastSamples.page).copy(refreshingShow = PodcastSamples.gece.id))
        compose.onNodeWithContentDescription("Yenileniyor").assertExists()
    }
}
