package app.nagmedar.ui.player.subtitles.providers

import app.nagmedar.ui.player.subtitles.FakeOpenSubtitles
import app.nagmedar.ui.player.subtitles.OnlineFailure
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuery
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.OnlineSubtitle
import app.nagmedar.ui.player.subtitles.OnlineSubtitlesController
import app.nagmedar.ui.player.subtitles.OkHttpOpenSubtitles
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** S-707: OpenSubtitles as a provider: its answers in the common model, searched as S-706 searched. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpenSubtitlesProviderTest {

    private val api = FakeOpenSubtitles()
    private val provider = OpenSubtitlesProvider(api)

    private val timed = OnlineSubtitle(
        11, "Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt", "Kuzey.Isigi.2024.1080p.WEB-DL", "tr", 12_345,
        hashMatch = true, hearingImpaired = true, fps = 23.976, title = "Kuzey Işığı", year = 2024, machineTranslated = true, rating = 0.8,
    )

    @Test
    fun itsAnswersAreInTheCommonModel() {
        api.byHash = listOf(timed)
        val found = runBlocking { provider.search(SubtitleQuery(hash = "8e245d9679d31e12", name = "Kuzey Isigi", languages = listOf("tr"))) }.single()
        assertEquals("opensubtitles/11", found.key)
        assertEquals("OpenSubtitles", found.providerName)
        assertEquals("Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt", found.fileName)
        assertEquals("Kuzey.Isigi.2024.1080p.WEB-DL", found.release)
        assertEquals("tr", found.language)
        assertEquals(12_345L, found.downloads)
        assertEquals(0.8, found.rating!!, 0.0001)
        assertTrue(found.hashMatch && found.hearingImpaired && found.machineTranslated)
        assertEquals(23.976, found.fps!!, 0.0001)
        assertEquals("Kuzey Işığı", found.title)
        assertEquals(2024, found.year)
        assertTrue(found.alsoFrom.isEmpty())
    }

    @Test
    fun theFingerprintFirstThenTheNameAsBefore() {
        api.byHash = emptyList()
        api.byName = listOf(timed.copy(hashMatch = false))
        val found = runBlocking { provider.search(SubtitleQuery(hash = "abc", name = "Kuzey Isigi", languages = listOf("tr", "en"))) }
        assertEquals(1, found.size)
        assertEquals(listOf(OnlineQuery("abc", null, listOf("tr", "en")), OnlineQuery(null, "Kuzey Isigi", listOf("tr", "en"))), api.queries)
    }

    @Test
    fun withoutAFingerprintOrAName() {
        runBlocking { provider.search(SubtitleQuery(name = "Kuzey Isigi", languages = listOf("en"))) }
        assertEquals(listOf(OnlineQuery(null, "Kuzey Isigi", listOf("en"))), api.queries)
        api.queries.clear()
        assertTrue(runBlocking { provider.search(SubtitleQuery(name = "  ", languages = listOf("en"))) }.isEmpty())
        assertTrue(api.queries.isEmpty())
    }

    @Test
    fun theSeriesAndTheImdbNumberGoWithTheNameSearch() {
        runBlocking { provider.search(SubtitleQuery(hash = null, name = "Dizi", imdbId = "tt0944947", season = 2, episode = 5, languages = listOf("tr"))) }
        assertEquals(listOf(OnlineQuery(null, "Dizi", listOf("tr"), "tt0944947", 2, 5)), api.queries)
        // an IMDb number alone is a question
        api.queries.clear()
        runBlocking { provider.search(SubtitleQuery(imdbId = "tt0944947", languages = listOf("tr"))) }
        assertEquals(1, api.queries.size)
    }

    @Test
    fun theApiAsksForTheParametersAlphabetically() {
        val seen = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                seen += chain.request().url.toString()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("""{"data":[]}""".toResponseBody()).build()
            },
        ).build()
        val real = OpenSubtitlesProvider(OkHttpOpenSubtitles("key", client = client, base = "https://api.example/v1/"))
        runBlocking { real.search(SubtitleQuery(name = "Dizi", imdbId = "tt0944947", season = 2, episode = 5, languages = listOf("TR", "en"))) }
        assertEquals("https://api.example/v1/subtitles?episode_number=5&imdb_id=944947&languages=en%2Ctr&order_by=download_count&query=dizi&season_number=2", seen.single())
        seen.clear()
        runBlocking { real.search(SubtitleQuery(hash = "ABC", languages = listOf("tr"))) }
        assertEquals("https://api.example/v1/subtitles?languages=tr&moviehash=abc&order_by=download_count", seen.single())
    }

    @Test
    fun aDownloadIsItsBytesAndTheQuotaItReported() {
        val file = runBlocking { provider.download(OpenSubtitlesProvider.resultOf(timed)) }
        assertEquals("Northern.Light.srt", file.fileName)
        assertTrue(file.bytes.isNotEmpty())
        assertEquals(OnlineQuota(4, 5, OnlineSubtitlesController.resetAt("2026-10-13T00:00:00.000Z")), file.quota)
    }

    @Test
    fun failuresAreThrownForTheSearchToReport() {
        api.failure = OnlineFailure.QuotaOver("2026-10-13T00:00:00.000Z")
        val thrown = runCatching { runBlocking { provider.download(OpenSubtitlesProvider.resultOf(timed)) } }.exceptionOrNull()
        assertTrue(thrown is OnlineFailure.QuotaOver)
    }

    @Test
    fun whatItCanDoAndWhatItAsksFor() {
        val c = provider.capabilities
        assertTrue(c.searchByHash && c.searchByImdb && c.searchByName && c.searchBySeasonEpisode && c.searchByLanguage && c.download)
        assertTrue(c.needsKey)
        assertFalse(c.needsLogin)
        assertEquals(5, c.rateLimit?.downloadsPerDay)
        assertEquals("opensubtitles", provider.id)
        assertNull(provider.unavailable())
        assertEquals(OnlineProblem.NoKey, OpenSubtitlesProvider(FakeOpenSubtitles(available = false)).unavailable())
    }
}
