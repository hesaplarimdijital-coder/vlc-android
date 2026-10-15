package app.nagmedar.ui.player.subtitles

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.subtitles.providers.FakeProvider
import app.nagmedar.ui.player.subtitles.providers.MemoryProviderStore
import app.nagmedar.ui.player.subtitles.providers.ProviderRegistry
import app.nagmedar.ui.player.subtitles.providers.ProviderReport
import app.nagmedar.ui.player.subtitles.providers.SubtitleSearch
import app.nagmedar.ui.player.subtitles.providers.result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/**
 * S-707: the search window over several providers. What each finds is one list, what fails is
 * told beside the provider's name while the others' results stay, a download goes to the
 * provider the file came from, and with none turned on nothing is asked.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnlineProvidersTest {

    private val alpha = FakeProvider(
        "alpha",
        "Alpha",
        results = listOf(
            result("alpha", "1", release = "Kuzey.Isigi.2024.1080p.WEB-DL", language = "tr", downloads = 900, hashMatch = true, providerName = "Alpha"),
            result("alpha", "2", release = "Kuzey.Isigi.2024.720p.BluRay", language = "tr", downloads = 50, providerName = "Alpha"),
        ),
    )
    private val beta = FakeProvider(
        "beta",
        "Beta",
        results = listOf(
            // the same file as alpha's first: one row, from both
            result("beta", "x", release = "Kuzey.Isigi.2024.1080p.WEB-DL", language = "tr", downloads = 10, providerName = "Beta"),
            result("beta", "y", release = "Northern.Light.2024.WEB", language = "en", downloads = 5000, providerName = "Beta"),
        ),
    )
    private val quotas = FakeQuotaStore()
    private val film = FakeOnlineFilm("Kuzey.Isigi.2024.1080p.WEB-DL.mkv")

    private fun controller(vararg providers: FakeProvider, store: MemoryProviderStore = MemoryProviderStore(), engine: SubtitleSearch = SubtitleSearch()) =
        OnlineSubtitlesController(ProviderRegistry(providers.toList(), store), quotas, CoroutineScope(Dispatchers.Unconfined), engine = engine)

    @Test
    fun theProvidersAnswersAreOneList() {
        val online = controller(alpha, beta)
        online.open(film, listOf("tr", "en"))
        val state = online.state!!
        assertEquals(listOf("alpha/1", "alpha/2", "beta/y"), state.results!!.map { it.key })
        assertEquals(listOf("Alpha", "Beta"), state.results!!.first().sources.split(" + ").sorted())
        assertEquals(listOf(ProviderReport("alpha", "Alpha", 2), ProviderReport("beta", "Beta", 2)), state.reports)
        assertNull(state.problem)
        assertEquals(1, alpha.queries.size)
        assertEquals(1, beta.queries.size)
        assertEquals(listOf("tr", "en"), beta.queries.single().languages)
    }

    @Test
    fun aProviderThatFailsIsToldAndTheOthersStay() {
        beta.failure = OnlineFailure.Offline(IOException("down"))
        val online = controller(alpha, beta)
        online.open(film, listOf("tr", "en"))
        val state = online.state!!
        assertEquals(listOf("alpha/1", "alpha/2"), state.results!!.map { it.key })
        assertEquals(OnlineProblem.Offline, state.reports.single { it.provider == "beta" }.problem)
        assertNull(state.problem)
    }

    @Test
    fun aSlowProviderTimesOutAndTheOthersStay() {
        beta.delayMs = 60_000
        val online = controller(alpha, beta, engine = SubtitleSearch(timeoutMs = 50))
        online.open(film, listOf("tr"))
        // the slow one is waited for in real time, 50 ms
        val until = System.currentTimeMillis() + 5_000
        while (online.state?.searching == true && System.currentTimeMillis() < until) Thread.sleep(10)
        val state = online.state!!
        assertEquals(2, state.results!!.size)
        assertEquals(OnlineProblem.Timeout, state.reports.single { it.provider == "beta" }.problem)
        assertNull(state.problem)
    }

    @Test
    fun whenEveryProviderFailsTheProblemIsTheWindows() {
        alpha.failure = OnlineFailure.Offline()
        beta.failure = OnlineFailure.Offline()
        val online = controller(alpha, beta)
        online.open(film, listOf("tr"))
        assertEquals(OnlineProblem.Offline, online.state?.problem)
        assertTrue(online.state?.results.orEmpty().isEmpty())
    }

    @Test
    fun aFolderNotChosenIsToldAsSuch() {
        val local = FakeProvider("local-folder", "Local folder", failure = OnlineFailure.NoFolder())
        val online = controller(local)
        online.open(film, listOf("tr"))
        assertEquals(OnlineProblem.NoFolder, online.state?.problem)
    }

    @Test
    fun withNoProviderOnNothingIsAsked() {
        val store = MemoryProviderStore(app.nagmedar.ui.player.subtitles.providers.ProviderSettings(off = setOf("alpha", "beta")))
        val online = controller(alpha, beta, store = store)
        assertEquals(OnlineProblem.NoProvider, online.unavailable)
        online.open(film, listOf("tr"))
        assertEquals(OnlineProblem.NoProvider, online.state?.problem)
        assertTrue(alpha.queries.isEmpty() && beta.queries.isEmpty())
    }

    @Test
    fun aProviderWithoutAKeyIsLeftOutWhileAnotherCanBeAsked() {
        alpha.problem = OnlineProblem.NoKey
        val online = controller(alpha, beta)
        assertNull(online.unavailable)
        online.open(film, listOf("tr", "en"))
        assertTrue(alpha.queries.isEmpty())
        assertEquals(OnlineProblem.NoKey, online.state!!.reports.single { it.provider == "alpha" }.problem)
        assertEquals(2, online.state!!.results!!.size)
    }

    @Test
    fun onlyTheNoKeyProviderLeftSaysSo() {
        alpha.problem = OnlineProblem.NoKey
        val online = controller(alpha)
        assertEquals(OnlineProblem.NoKey, online.unavailable)
        online.open(film, listOf("tr"))
        assertEquals(OnlineProblem.NoKey, online.state?.problem)
    }

    @Test
    fun aDownloadGoesToTheProviderTheFileCameFrom() {
        val online = controller(alpha, beta)
        online.open(film, listOf("tr", "en"))
        val english = online.state!!.results!!.single { it.provider == "beta" }
        online.download(english)
        assertEquals(listOf("y"), beta.downloads.map { it.id })
        assertTrue(alpha.downloads.isEmpty())
        assertEquals(listOf("Kuzey.Isigi.2024.1080p.WEB-DL.en.srt"), film.saved)
        assertNull(online.state)
    }

    @Test
    fun aFailedDownloadIsToldAndTheListStays() {
        val online = controller(alpha, beta)
        online.open(film, listOf("tr", "en"))
        val wanted = online.state!!.results!!.first()
        alpha.failure = OnlineFailure.Server(500)
        online.download(wanted)
        assertEquals(OnlineProblem.Server, online.state?.problem)
        assertNull(online.state?.downloading)
        assertEquals(3, online.state?.results?.size)
    }
}
