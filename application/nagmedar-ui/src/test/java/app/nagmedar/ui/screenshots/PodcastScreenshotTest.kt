package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.podcast.AddAddressSurface
import app.nagmedar.ui.podcast.AddUi
import app.nagmedar.ui.podcast.DownloadUi
import app.nagmedar.ui.podcast.EpisodeDetailUi
import app.nagmedar.ui.podcast.ExtraUi
import app.nagmedar.ui.podcast.FeedFailure
import app.nagmedar.ui.podcast.FeedUrl
import app.nagmedar.ui.podcast.ImportFailure
import app.nagmedar.ui.podcast.ImportSummary
import app.nagmedar.ui.podcast.ImportSurface
import app.nagmedar.ui.podcast.ImportUi
import app.nagmedar.ui.podcast.InvalidReason
import app.nagmedar.ui.podcast.Listening
import app.nagmedar.ui.podcast.PodcastActions
import app.nagmedar.ui.podcast.PodcastSamples
import app.nagmedar.ui.podcast.PodcastScreenModel
import app.nagmedar.ui.podcast.PodcastsContent
import app.nagmedar.ui.podcast.SearchFailure
import app.nagmedar.ui.podcast.SearchNotice
import app.nagmedar.ui.podcast.SearchUi
import app.nagmedar.ui.podcast.SubscribeUi
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1101, S-1102, for approval: the subscriptions as covers (DeX, tablet) and as a list (phone), the
 * search results with every state of the Subscribe button and the notice that iTunes answered alone,
 * the podcast page with its episodes, the empty, offline and no-result screens, "RSS adresiyle ekle"
 * with the reason a feed was refused, and the OPML import's summary; an episode's transcript tab with the feed's
 * transcript and without one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) = capture(name, theme, input, {}, content)

    /** [content] drawn, [before] done on it (a tab chosen, a scroll), then the picture taken. */
    private fun capture(name: String, theme: ThemeMode, input: InputMode, before: () -> Unit, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    private fun shell(width: WidthClass, model: PodcastScreenModel): @Composable () -> Unit = {
        TestShell(width, startDestination = Destination.Podcasts) { _, _ -> PodcastsContent(model, PodcastActions()) }
    }

    private val results = PodcastSamples.hits
    private fun key(index: Int) = FeedUrl.key(results[index].feedUrl)

    private fun found(notice: SearchNotice? = null) = SearchUi.Found("gece", results, notice)

    private val states = mapOf(
        key(2) to SubscribeUi.Working,
        key(3) to SubscribeUi.Failed(FeedFailure.Invalid(InvalidReason.NotFeed)),
    )

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun gridDex() = capture("podcasts-grid-dex-dark", ThemeMode.Dark, InputMode.Pointer, shell(WidthClass.Large, PodcastSamples.model()))

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun gridTablet() = capture("podcasts-grid-tablet-light", ThemeMode.Light, InputMode.Touch, shell(WidthClass.Expanded, PodcastSamples.model()))

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun listPhoneEnglish() = capture("podcasts-list-phone-light-en", ThemeMode.Light, InputMode.Touch, shell(WidthClass.Compact, PodcastSamples.model()))

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun resultsDex() = capture(
        "podcasts-results-dex-light", ThemeMode.Light, InputMode.Pointer,
        shell(
            WidthClass.Large,
            PodcastSamples.model(
                subscriptions = PodcastSamples.summaries,
                query = "gece",
                search = found(SearchNotice.PodcastIndexMissing),
                subscribing = states,
            ),
        ),
    )

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun resultsPhone() = capture(
        "podcasts-results-phone-dark", ThemeMode.Dark, InputMode.Touch,
        shell(WidthClass.Compact, PodcastSamples.model(query = "gece", search = found(), subscribing = states)),
    )

    @Test @Config(qualifiers = "en-w1000dp-h800dp-mdpi")
    fun resultsTabletEnglish() = capture(
        "podcasts-results-tablet-dark-en", ThemeMode.Dark, InputMode.Touch,
        shell(WidthClass.Expanded, PodcastSamples.model(query = "gece", search = found(SearchNotice.PodcastIndexFailed), subscribing = states)),
    )

    @Test @Config(qualifiers = "tr-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun pageDex() = capture(
        "podcasts-page-dex-dark", ThemeMode.Dark, InputMode.Pointer,
        shell(WidthClass.Large, PodcastSamples.model(page = PodcastSamples.page)),
    )

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun pagePhone() = capture(
        "podcasts-page-phone-light", ThemeMode.Light, InputMode.Touch,
        shell(WidthClass.Compact, PodcastSamples.model(page = PodcastSamples.page)),
    )

    /** S-205 on a phone as narrow as the user's: play and download in one row, the timeline and its skips under them, the tabs. */
    @Test @Config(qualifiers = "tr-w350dp-h760dp-xxhdpi")
    fun episodePhone() = capture(
        "podcasts-episode-phone-light", ThemeMode.Light, InputMode.Touch,
        shell(
            WidthClass.Compact,
            PodcastSamples.model(page = PodcastSamples.page).copy(
                episodeId = 10,
                episode = PodcastSamples.episodePage(listening = Listening(positionMs = 6 * 60_000L, lengthMs = 62 * 60_000L + 3_000, playing = true)),
            ),
        ),
    )

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun episodePhoneDarkEnglish() = capture(
        "podcasts-episode-phone-dark-en", ThemeMode.Dark, InputMode.Touch,
        shell(
            WidthClass.Compact,
            PodcastSamples.model(page = PodcastSamples.page).copy(
                episodeId = 10,
                episode = PodcastSamples.episodePage(download = DownloadUi.Done(58_000_000), positionMs = 23 * 60_000L),
            ),
        ),
    )

    /** The episode's "Döküm" tab ([tab], the tab's word in the language of the run), scrolled to [tag]. */
    private fun transcriptTab(tab: String, tag: String): () -> Unit = {
        compose.onNode(hasText(tab) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("episode-detail").performScrollToNode(hasTestTag(tag))
    }

    private fun episodeModel(ui: EpisodeDetailUi) = PodcastSamples.model(page = PodcastSamples.page).copy(episodeId = 10, episode = ui)

    /** A show that gives no transcript, on a phone: the tab says so, and offers nothing. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun transcriptNonePhone() = capture(
        "podcasts-transcript-none-phone-light", ThemeMode.Light, InputMode.Touch, transcriptTab("Döküm", "episode-transcript-none"),
        shell(WidthClass.Compact, episodeModel(PodcastSamples.episodePage())),
    )

    /** The feed's transcript, followed while the episode plays (English, phone). */
    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun transcriptPhoneEnglish() = capture(
        "podcasts-transcript-phone-dark-en", ThemeMode.Dark, InputMode.Touch, transcriptTab("Transcript", "episode-transcript-search"),
        shell(
            WidthClass.Compact,
            episodeModel(
                PodcastSamples.episodePage(listening = Listening(positionMs = 17_000, lengthMs = 62 * 60_000L + 3_000, playing = true))
                    .copy(transcript = ExtraUi.Ready(PodcastSamples.feedTranscript)),
            ),
        ),
    )

    /** The feed's transcript beside the episodes. */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun transcriptTablet() = capture(
        "podcasts-transcript-tablet-light", ThemeMode.Light, InputMode.Touch, transcriptTab("Döküm", "episode-transcript-search"),
        shell(
            WidthClass.Expanded,
            episodeModel(PodcastSamples.episodePage(download = DownloadUi.Done(58_000_000)).copy(transcript = ExtraUi.Ready(PodcastSamples.feedTranscript))),
        ),
    )

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun emptyTablet() = capture("podcasts-empty-tablet-light", ThemeMode.Light, InputMode.Touch, shell(WidthClass.Expanded, PodcastSamples.model(subscriptions = emptyList())))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun offlinePhone() = capture(
        "podcasts-offline-phone-dark", ThemeMode.Dark, InputMode.Touch,
        shell(WidthClass.Compact, PodcastSamples.model(query = "gece", search = SearchUi.Failed("gece", SearchFailure.Offline))),
    )

    @Test @Config(qualifiers = "en-w1440dp-h900dp-mdpi-keysexposed-qwerty")
    fun noResultsDexEnglish() = capture(
        "podcasts-no-results-dex-light-en", ThemeMode.Light, InputMode.Pointer,
        shell(WidthClass.Large, PodcastSamples.model(query = "zzzz", search = SearchUi.Found("zzzz", emptyList(), SearchNotice.PodcastIndexMissing))),
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun addRefused() = capture("podcasts-add-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AddAddressSurface(AddUi("https://example.org/", failure = FeedFailure.Invalid(InvalidReason.NotFeed)), onSubmit = {}, onDismiss = {})
        }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun addReadingPhoneEnglish() = capture("podcasts-add-phone-light-en", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AddAddressSurface(AddUi("feed://gece.example.org/rss", working = true), onSubmit = {}, onDismiss = {})
        }
    }

    private val summary = ImportSummary(
        added = 12,
        already = 3,
        failed = listOf(
            ImportFailure("Bozuk Yayın", "https://bozuk.example.org/rss", FeedFailure.Invalid(InvalidReason.NotFeed)),
            ImportFailure("Kapanmış Yayın", "https://kapali.example.org/rss", FeedFailure.Server(404)),
        ),
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun importDone() = capture("podcasts-import-done-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ImportSurface(ImportUi.Done(summary), onDismiss = {}) }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-xhdpi")
    fun importRunningPhone() = capture("podcasts-import-running-phone-light", ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ImportSurface(ImportUi.Running(37, 120), onDismiss = {}) }
    }

    @Test @Config(qualifiers = "en-w411dp-h891dp-xhdpi")
    fun importUnusablePhoneEnglish() = capture("podcasts-import-unusable-phone-dark-en", ThemeMode.Dark, InputMode.Touch) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ImportSurface(ImportUi.Unusable(app.nagmedar.ui.podcast.OpmlProblem.NotOpml), onDismiss = {})
        }
    }
}
