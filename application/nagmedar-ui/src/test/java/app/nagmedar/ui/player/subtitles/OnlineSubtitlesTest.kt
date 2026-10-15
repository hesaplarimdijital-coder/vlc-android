package app.nagmedar.ui.player.subtitles

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.subtitles.providers.OpenSubtitlesProvider
import app.nagmedar.ui.player.subtitles.providers.ProviderRegistry
import app.nagmedar.ui.player.subtitles.providers.ProviderSettings
import app.nagmedar.ui.player.subtitles.providers.ProviderSettingsStore
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/** OpenSubtitles as the tests have it: what each search finds, what each download answers. */
internal class FakeOpenSubtitles(override val available: Boolean = true) : OpenSubtitlesApi {
    val queries = mutableListOf<OnlineQuery>()
    var byHash: List<OnlineSubtitle> = emptyList()
    var byName: List<OnlineSubtitle> = emptyList()
    var failure: Exception? = null
    var ticket = OnlineTicket("https://dl.example/1", "Northern.Light.srt", remaining = 4, requests = 1, resetUtc = "2026-10-13T00:00:00.000Z")

    override suspend fun search(query: OnlineQuery): List<OnlineSubtitle> {
        queries += query
        failure?.let { throw it }
        return if (query.hash != null) byHash else byName
    }

    override suspend fun download(fileId: Long): OnlineTicket {
        failure?.let { throw it }
        return ticket
    }

    override suspend fun fetch(link: String): ByteArray = SAMPLE_SRT.toByteArray()
}

internal class FakeOnlineFilm(override val name: String = "Kuzey Işığı.mkv", private val fingerprint: String? = "8e245d9679d31e12") : OnlineFilm {
    val saved = mutableListOf<String>()
    var beside = true
    var loads = true

    override suspend fun hash(): String? = fingerprint

    override suspend fun save(fileName: String, bytes: ByteArray): SavedSubtitle {
        saved += fileName
        return SavedSubtitle("file:///films/$fileName", fileName, beside)
    }

    override suspend fun load(saved: SavedSubtitle): Boolean = loads
}

internal class FakeQuotaStore(var kept: OnlineQuota? = null) : OnlineQuotaStore {
    override fun load(): OnlineQuota? = kept

    override fun save(quota: OnlineQuota) {
        kept = quota
    }
}

private const val SAMPLE_SRT = "1\n00:00:01,000 --> 00:00:04,000\nGece yarısı Kuzey Işığı'nda buluşalım.\n"

/** Turkish timed for the file, English by name, Turkish by name: as OpenSubtitles' API gives them. */
internal val sampleApi = listOf(
    OnlineSubtitle(11, "Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt", "Kuzey.Isigi.2024.1080p.WEB-DL", "tr", 12_345, hashMatch = true, fps = 23.976, title = "Kuzey Işığı", year = 2024),
    OnlineSubtitle(12, "Northern.Light.2024.1080p.WEB.srt", "Northern.Light.2024.1080p.WEB", "en", 48_210, fps = 23.976, title = "Northern Light", year = 2024),
    OnlineSubtitle(13, "Kuzey.Isigi.2024.720p.BluRay.tr.srt", "Kuzey.Isigi.2024.720p.BluRay", "tr", 3_120, hearingImpaired = true, fps = 25.0, title = "Kuzey Işığı", year = 2024),
)

/** The same, in the providers' common model. */
internal val sampleOnline = sampleApi.map { OpenSubtitlesProvider.resultOf(it) }

internal class FakeProviderStore(var kept: ProviderSettings = ProviderSettings()) : ProviderSettingsStore {
    override fun load(): ProviderSettings = kept

    override fun save(settings: ProviderSettings) {
        kept = settings
    }
}

/** A registry of OpenSubtitles alone, as S-706 had it. */
internal fun openSubtitlesOnly(api: OpenSubtitlesApi) = ProviderRegistry(listOf(OpenSubtitlesProvider(api)), FakeProviderStore())

/**
 * S-706: OpenSubtitles.com searched by the file's fingerprint, then by its name; the fingerprint's
 * matches first, then the languages chosen; a download saved beside the film (or in the app's
 * folder) and shown, the day's quota kept as OpenSubtitles tells it; what went wrong said. The
 * answers' JSON is read as the API writes it; the fingerprint is OpenSubtitles' own.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class OnlineSubtitlesTest {

    @get:Rule
    val compose = createComposeRule()

    private val api = FakeOpenSubtitles()
    private val quotas = FakeQuotaStore()
    private val online = OnlineSubtitlesController(openSubtitlesOnly(api), quotas, CoroutineScope(Dispatchers.Unconfined), now = { NOW })

    @Test
    fun theFingerprintFirstThenTheName() {
        api.byHash = listOf(sampleApi[0])
        val film = FakeOnlineFilm("Kuzey.Isigi.2024.1080p.WEB-DL.mkv")
        online.open(film, listOf("tr", "en"))
        assertEquals(listOf(OnlineQuery("8e245d9679d31e12", null, listOf("tr", "en"))), api.queries)
        assertEquals(listOf(sampleOnline[0]), online.state?.results)
        assertEquals("Kuzey Isigi 2024 1080p WEB-DL", online.state?.name)

        // nothing timed for the file: its name
        api.byHash = emptyList()
        api.byName = sampleApi.reversed()
        api.queries.clear()
        online.search()
        assertEquals(2, api.queries.size)
        assertEquals("Kuzey Isigi 2024 1080p WEB-DL", api.queries[1].name)
        assertEquals(listOf(11L, 13L, 12L), online.state?.results?.map { it.id.toLong() })

        // without the fingerprint, and in English only
        online.setByHash(false)
        online.toggle("tr")
        api.queries.clear()
        online.search()
        assertEquals(listOf(OnlineQuery(null, "Kuzey Isigi 2024 1080p WEB-DL", listOf("en"))), api.queries)
    }

    @Test
    fun aDownloadGoesBesideTheFilmAndShows() {
        api.byHash = sampleApi
        val film = FakeOnlineFilm()
        online.open(film, listOf("tr", "en"))
        online.download(sampleOnline[0])
        assertEquals(listOf("Kuzey Işığı.tr.srt"), film.saved)
        assertNull("done: the window closes", online.state)
        assertEquals(OnlineQuota(4, 5, OnlineSubtitlesController.resetAt("2026-10-13T00:00:00Z")), quotas.kept)
        assertEquals(4, online.remaining())
        // the day over: all of them again
        assertEquals(5, OnlineQuota(0, 5, NOW - 1).remainingAt(NOW))
    }

    @Test
    fun whatWentWrongIsSaid() {
        api.failure = OnlineFailure.Offline()
        online.open(FakeOnlineFilm(), listOf("tr", "en"))
        assertEquals(OnlineProblem.Offline, online.state?.problem)
        assertFalse(online.state!!.searching)

        api.failure = null
        api.byHash = sampleApi
        online.search()
        api.failure = OnlineFailure.QuotaOver("2026-10-13T00:00:00.000Z")
        online.download(sampleOnline[1])
        assertEquals(OnlineProblem.QuotaOver, online.state?.problem)
        assertEquals(0, quotas.kept?.remaining)
        assertEquals(OnlineSubtitlesController.ANONYMOUS_DOWNLOADS, quotas.kept?.allowed)
        assertNull(online.state?.downloading)

        // saved nowhere
        api.failure = null
        val film = FakeOnlineFilm().apply { loads = false }
        online.open(film, listOf("tr"))
        online.download(sampleOnline[0])
        assertEquals(OnlineProblem.Unsaved, online.state?.problem)
    }

    @Test
    fun withoutAKeyNothingIsAsked() {
        val none = FakeOpenSubtitles(available = false)
        val search = OnlineSubtitlesController(openSubtitlesOnly(none), quotas, CoroutineScope(Dispatchers.Unconfined))
        search.open(FakeOnlineFilm(), listOf("tr", "en"))
        assertEquals(OnlineProblem.NoKey, search.state?.problem)
        assertTrue(none.queries.isEmpty())
    }

    @Test
    fun namesAndLanguages() {
        assertEquals("Kuzey Işığı.tr.srt", OnlineSubtitlesController.fileNameFor("Kuzey Işığı.mkv", sampleOnline[0], "x.srt"))
        assertEquals("Film.en.ass", OnlineSubtitlesController.fileNameFor("Film.mp4", sampleOnline[1], "Film.ASS"))
        assertEquals("Film.en.srt", OnlineSubtitlesController.fileNameFor("Film.mp4", sampleOnline[1], "Film.zip"))
        assertEquals(listOf("tr", "en"), onlineLanguages(Locale.forLanguageTag("tr-TR")))
        assertEquals(listOf("pt-BR", "en", "tr"), onlineLanguages(Locale.forLanguageTag("pt-BR")))
        assertEquals(listOf("en", "tr"), onlineLanguages(Locale.UK))
        assertEquals("İngilizce", languageName("en", Locale.forLanguageTag("tr")))
    }

    @Test
    fun theAnswersAreReadAsTheApiWritesThem() {
        val found = OpenSubtitlesJson.results(SEARCH_JSON)
        assertEquals(2, found.size)
        val first = found[0]
        assertEquals(9_015_442L, first.fileId)
        assertEquals("tr", first.language)
        assertEquals("Kuzey.Isigi.2024.1080p.WEB-DL", first.release)
        assertTrue(first.hashMatch)
        assertEquals(23.976, first.fps!!, 0.0001)
        assertEquals(2024, first.year)
        assertEquals(1_234L, first.downloads)
        // a null release, an AI translation, no frame rate
        assertEquals("", found[1].release)
        assertTrue(found[1].machineTranslated)
        assertNull(found[1].fps)

        val ticket = OpenSubtitlesJson.ticket(DOWNLOAD_JSON)
        assertEquals("https://www.opensubtitles.com/download/abc/subfile/Kuzey.srt", ticket.link)
        assertEquals(4, ticket.remaining)
        assertEquals(1, ticket.requests)
        assertEquals("2026-10-13T00:00:00.000Z", ticket.resetUtc)
        assertEquals("2026-10-13T00:00:00.000Z", OpenSubtitlesJson.resetTime(QUOTA_JSON))
        assertEquals(1_760_313_600_000L, OnlineSubtitlesController.resetAt("2025-10-13T00:00:00.000Z"))
    }

    @Test
    fun theFingerprintIsOpenSubtitlesOwn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "film.bin")
        file.writeBytes(ByteArray(200 * 1024) { ((it * 7 + 3) % 251).toByte() })
        assertEquals("374866758397b267", RandomAccessFile(file, "r").use { movieHash(it.channel) })
        // smaller than the 64 KiB read at each end
        file.writeBytes(ByteArray(1000) { ((it * 7 + 3) % 251).toByte() })
        assertEquals("050613141f2c30a8", RandomAccessFile(file, "r").use { movieHash(it.channel) })
        file.delete()
    }

    @Test
    fun theQuotaIsKeptInTheSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PrefsOnlineQuotaStore(context.getSharedPreferences("online-test", Context.MODE_PRIVATE))
        assertNull(store.load())
        store.save(OnlineQuota(14, 20, 1_760_313_600_000L))
        assertEquals(OnlineQuota(14, 20, 1_760_313_600_000L), store.load())
        store.save(OnlineQuota(3, 5, null))
        assertEquals(OnlineQuota(3, 5, null), store.load())
    }

    private val actions = mutableListOf<String>()

    private fun dialog(state: OnlineSearchState, quota: OnlineQuota? = OnlineQuota(14, 20, null)) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(420.dp)) {
                    OnlineSubtitlesSurface(
                        state = state,
                        quota = quota,
                        nowMs = NOW,
                        onName = { actions += "name:$it" },
                        onLanguage = { actions += "language:$it" },
                        onByHash = { actions += "hash:$it" },
                        onSearch = { actions += "search" },
                        onDownload = { actions += "download:${it.id}" },
                        onDismiss = { actions += "close" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theWindowListsWhatWasFoundAndDownloadsIt() {
        dialog(OnlineSearchState("Kuzey Işığı 2024", listOf("tr", "en"), listOf("tr", "en"), results = sampleOnline))
        compose.onNodeWithText("Kuzey.Isigi.2024.1080p.WEB-DL").assertExists()
        compose.onNodeWithText("bu dosyaya uygun · 12.345 indirme · 23,976 fps").assertExists()
        compose.onNodeWithText("OpenSubtitles kotası: 14/20").assertExists()
        compose.onNodeWithTag("online-result-opensubtitles/13").performClick()
        compose.onNodeWithText("İngilizce").performClick()
        compose.onNodeWithTag("online-search").performClick()
        assertEquals(listOf("download:13", "language:en", "search"), actions)
    }

    @Test
    fun theWindowSaysWhatWentWrong() {
        dialog(OnlineSearchState("Kuzey Işığı", listOf("tr"), listOf("tr", "en"), problem = OnlineProblem.NoKey))
        compose.onNodeWithText("Bu derlemede OpenSubtitles anahtarı yok").assertExists()
        compose.onNodeWithTag("online-search").assertIsNotEnabled()
    }

    @Test
    fun theTabsRowSaysTheQuota() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                OnlineSection(available = true, quota = OnlineQuota(14, 20, null), nowMs = NOW) { actions += "open" }
            }
        }
        compose.onNodeWithText("OpenSubtitles kotası: 14/20").assertExists()
        compose.onNodeWithTag("online-open").performClick()
        assertEquals(listOf("open"), actions)
    }

    private companion object {
        const val NOW = 1_760_300_000_000L

        val SEARCH_JSON = """
            {"total_pages":1,"total_count":2,"per_page":60,"page":1,"data":[
              {"id":"7212000","type":"subtitle","attributes":{"subtitle_id":"7212000","language":"tr","download_count":1234,
                "hearing_impaired":false,"hd":true,"fps":23.976,"votes":0,"ratings":0.0,"from_trusted":true,
                "foreign_parts_only":false,"ai_translated":false,"machine_translated":false,"moviehash_match":true,
                "release":"Kuzey.Isigi.2024.1080p.WEB-DL",
                "feature_details":{"feature_id":1,"feature_type":"Movie","year":2024,"title":"Kuzey Işığı","movie_name":"2024 - Kuzey Işığı"},
                "files":[{"file_id":9015442,"cd_number":1,"file_name":"Kuzey.Isigi.2024.1080p.WEB-DL.tr.srt"}]}},
              {"id":"7212001","type":"subtitle","attributes":{"subtitle_id":"7212001","language":"en","download_count":56,
                "hearing_impaired":false,"fps":0,"ai_translated":true,"release":null,
                "feature_details":{"title":"Northern Light","year":null},
                "files":[{"file_id":9015443,"file_name":"Northern.Light.srt"}]}},
              {"id":"7212002","type":"subtitle","attributes":{"language":"de","files":[]}}
            ]}
        """.trimIndent()

        val DOWNLOAD_JSON = """
            {"link":"https://www.opensubtitles.com/download/abc/subfile/Kuzey.srt","file_name":"Kuzey.srt","requests":1,
             "remaining":4,"message":"Your quota will be renewed in 06 hours and 12 minutes (2026-10-13 00:00:00 UTC) ",
             "reset_time":"06 hours and 12 minutes","reset_time_utc":"2026-10-13T00:00:00.000Z"}
        """.trimIndent()

        val QUOTA_JSON = """
            {"requests":6,"remaining":-1,"message":"You have downloaded your allowed 5 subtitles for 24h.",
             "reset_time":"06 hours and 12 minutes","reset_time_utc":"2026-10-13T00:00:00.000Z"}
        """.trimIndent()
    }
}
