package app.nagmedar.ui.podcast

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
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
 * S-1101, S-1102: the Podcasts screen answers the person: one tap on a result subscribes, Enter
 * searches, an episode plays, and the "add by address" window checks what is typed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class PodcastScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var model by mutableStateOf(PodcastSamples.model())

    /** The window shows the feed being read once the address is sent, as the controller's does. */
    private var readsOnSubmit = false

    private val actions = PodcastActions(
        onQueryChange = { calls += "query:$it" },
        onSearch = { calls += "search" },
        onClearSearch = { calls += "clear" },
        onSubscribe = { calls += "subscribe:${it.title}" },
        onOpen = { calls += "open:$it" },
        onOpenSubscribed = { calls += "open-hit:${it.title}" },
        onUnsubscribe = { calls += "unsubscribe:$it" },
        onPlay = { _: PodcastFeed, episode: PodcastEpisode -> calls += "play:${episode.title}" },
        onOpenEpisode = { calls += "open-episode:$it" },
        onSetPlayed = { id, played -> calls += "played:$id:$played" },
        onAdd = { calls += "add" },
        onAddSubmit = {
            calls += "submit:$it"
            if (readsOnSubmit) model = PodcastSamples.model(add = AddUi(it, working = true))
        },
        onAddDismiss = { calls += "dismiss" },
        onImport = { calls += "import" },
        onClosePage = { calls += "close" },
    )

    private fun show() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) { PodcastsContent(model, actions) }
        }
    }

    private val results = PodcastSamples.hits

    private fun found() = PodcastSamples.model(
        query = "gece",
        search = SearchUi.Found("gece", results, null),
        subscribing = mapOf(
            FeedUrl.key(results[2].feedUrl) to SubscribeUi.Working,
            FeedUrl.key(results[3].feedUrl) to SubscribeUi.Failed(FeedFailure.Invalid(InvalidReason.NotFeed)),
        ),
    )

    @Test
    fun oneTapSubscribes() {
        model = found()
        show()
        // "Gece Yayını" is a subscription already; the others offer "Abone ol", one is being added, one failed
        compose.onAllNodesWithTag("podcast-result").assertCountEquals(results.size)
        compose.onAllNodesWithTag("podcast-subscribed").assertCountEquals(1)
        compose.onAllNodesWithTag("podcast-subscribe-working").assertCountEquals(1)
        compose.onAllNodesWithTag("podcast-subscribe").assertCountEquals(3)
        compose.onAllNodesWithTag("podcast-subscribe")[0].performClick()
        assertEquals(listOf("subscribe:Gece Kuşları"), calls)
    }

    @Test
    fun aRefusedFeedShowsItsReason() {
        model = found()
        show()
        compose.onNodeWithTag("podcast-result-warning", useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("podcast-result-warning") and hasText(compose.activity.getString(app.nagmedar.ui.R.string.nag_pod_fail_not_feed)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aSubscribedResultOpensItsPage() {
        model = found()
        show()
        compose.onNodeWithTag("podcast-subscribed").performClick()
        assertEquals(listOf("open-hit:Gece Yayını"), calls)
    }

    @Test
    fun enterSearches() {
        show()
        compose.onNodeWithTag("podcast-search").performTextInput("gece")
        compose.onNodeWithTag("podcast-search").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("query:gece", "search"), calls)
    }

    @Test
    fun theSearchButtonWaitsForSomethingToSearch() {
        show()
        compose.onNodeWithTag("podcast-search-button").assertIsNotEnabled()
        model = PodcastSamples.model(query = "gece")
        compose.onNodeWithTag("podcast-search-button").assertIsEnabled()
    }

    @Test
    fun theSubscriptionsAreCoversWithTheirNewEpisodes() {
        show()
        compose.onAllNodesWithTag("podcast-tile").assertCountEquals(PodcastSamples.summaries.size)
        // two subscriptions have new episodes
        compose.onAllNodesWithTag("podcast-new", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag("podcast-tile")[0].performClick()
        assertEquals(listOf("open:${PodcastSamples.summaries[0].feed.id}"), calls)
    }

    @Test
    fun theViewTogglesToAList() {
        show()
        compose.onNodeWithTag("podcast-view-toggle").performClick()
        compose.onAllNodesWithTag("podcast-row").assertCountEquals(PodcastSamples.summaries.size)
    }

    @Test
    fun aPodcastPlaysItsEpisodes() {
        model = PodcastSamples.model(page = PodcastSamples.page)
        show()
        compose.onAllNodesWithTag("podcast-episode").assertCountEquals(PodcastSamples.episodes.size)
        // three are new, two listened to
        compose.onAllNodesWithTag("podcast-episode-new", useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithTag("podcast-play")[0].performClick()
        compose.onAllNodesWithTag("podcast-episode")[1].performClick()
        compose.onNodeWithTag("podcast-unsubscribe").performClick()
        // a tap on the row opens the episode's page; playing is the play button's
        assertEquals(listOf("play:Üçüncü gece: Neva Kâr", "open-episode:9", "unsubscribe:${PodcastSamples.gece.id}"), calls)
    }

    @Test
    fun theAddWindowChecksTheAddress() {
        model = PodcastSamples.model(add = AddUi())
        show()
        compose.onNodeWithTag("podcast-add-submit").assertIsNotEnabled()
        compose.onNodeWithTag("podcast-add-field").performTextInput("sadece sözcükler")
        compose.onNodeWithTag("podcast-add-warning").assertExists()
        compose.onNodeWithTag("podcast-add-submit").assertIsNotEnabled()
    }

    @Test
    fun theAddWindowSubmitsOnEnter() {
        model = PodcastSamples.model(add = AddUi())
        show()
        compose.onNodeWithTag("podcast-add-field").performTextInput("feed://example.org/rss")
        compose.onNodeWithTag("podcast-add-submit").assertIsEnabled()
        compose.onNodeWithTag("podcast-add-field").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("submit:feed://example.org/rss"), calls)
    }

    @Test
    fun enterReleasedWhileTheFeedIsReadIsNoCancel() {
        // 1.0.1: Enter's press sent the address and the field, disabled while the feed was read, gave the focus away;
        // Enter's release then clicked "Cancel", and closing the window cancelled the subscription
        readsOnSubmit = true
        model = PodcastSamples.model(add = AddUi())
        show()
        val field = compose.onNodeWithTag("podcast-add-field")
        field.performTextInput("feed://example.org/rss")
        field.performKeyInput { keyDown(Key.Enter) }
        compose.waitForIdle()
        field.performKeyInput {
            advanceEventTime(150)
            keyUp(Key.Enter)
        }
        compose.waitForIdle()
        assertEquals(listOf("submit:feed://example.org/rss"), calls)
        field.assertIsFocused()
    }

    @Test
    fun theAddWindowShowsWhyAFeedWasRefused() {
        model = PodcastSamples.model(add = AddUi("https://example.org/", failure = FeedFailure.Invalid(InvalidReason.NotFeed)))
        show()
        compose.onNodeWithTag("podcast-add-warning").assertExists()
        compose.onNodeWithTag("podcast-add-submit").assertIsEnabled()
    }
}
