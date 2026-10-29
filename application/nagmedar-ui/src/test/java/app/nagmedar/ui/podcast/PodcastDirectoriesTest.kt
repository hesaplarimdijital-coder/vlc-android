package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** S-1101: Podcast Index's request signing, the directories' answers read, and the two merged. */
@RunWith(AndroidJUnit4::class)
class PodcastDirectoriesTest {

    // ------------------------------------------------------------ Podcast Index's headers

    @Test
    fun theAuthorizationIsTheSha1OfKeySecretAndTime() {
        // sha1("ABCSECRET1700000000")
        val headers = PodcastIndexAuth.headers("ABC", "SECRET", "Nagmedar/1.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", 1_700_000_000L)
        assertEquals("e31761d681465c8f935fa442a83511ab6b22e23c", headers["Authorization"])
        assertEquals("ABC", headers["X-Auth-Key"])
        assertEquals("1700000000", headers["X-Auth-Date"])
        assertEquals("Nagmedar/1.0 (+https://github.com/hesaplarimdijital-coder/vlc-android)", headers["User-Agent"])
        assertEquals(4, headers.size)
    }

    @Test
    fun sha1OfTheKnownString() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", PodcastIndexAuth.sha1Hex("abc"))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", PodcastIndexAuth.sha1Hex(""))
    }

    // ------------------------------------------------------------ the answers

    @Test
    fun podcastIndexSearch() {
        val hits = PodcastIndexJson.hits(
            """
            {"status":"true","count":4,"query":"gece","feeds":[
              {"id":920666,"title":"Gece &amp; Müzik","url":"https://feeds.example.org/gece.xml","author":"Gece Ekibi",
               "image":"https://gece.example.org/kapak.jpg","artwork":"https://gece.example.org/a.jpg","episodeCount":42,
               "description":"<p>Her hafta</p>","dead":0},
              {"id":2,"title":"Ölü yayın","url":"https://feeds.example.org/olu.xml","dead":1},
              {"id":3,"title":"Adressiz","url":""},
              {"id":4,"title":"Boş görsel","url":"https://feeds.example.org/bos.xml","ownerName":"Sahip","image":"","artwork":"not a url","episodeCount":null}
            ]}
            """.trimIndent(),
        )
        assertEquals(2, hits.size)
        val first = hits[0]
        assertEquals("https://feeds.example.org/gece.xml", first.feedUrl)
        assertEquals("Gece & Müzik", first.title)
        assertEquals("Gece Ekibi", first.author)
        assertEquals("https://gece.example.org/kapak.jpg", first.imageUrl)
        assertEquals(42, first.episodeCount)
        assertEquals("Her hafta", first.description)
        assertEquals(920666L, first.podcastIndexId)
        assertEquals(HitSource.PodcastIndex, first.source)
        val second = hits[1]
        assertEquals("Sahip", second.author)
        assertNull(second.imageUrl)
        assertNull(second.episodeCount)
    }

    @Test
    fun podcastIndexAnswersWithNothingOrNonsense() {
        assertEquals(emptyList<PodcastHit>(), PodcastIndexJson.hits("""{"status":"true","feeds":[],"count":0}"""))
        assertEquals(emptyList<PodcastHit>(), PodcastIndexJson.hits("""{"status":"false","description":"x"}"""))
        try {
            PodcastIndexJson.hits("<html>bad gateway</html>")
            fail("read as JSON")
        } catch (e: DirectoryFailure.Server) {
            assertEquals(200, e.status)
        }
    }

    @Test
    fun podcastIndexByFeedUrl() {
        val hit = PodcastIndexJson.byFeedUrl("""{"status":"true","feed":{"id":77,"title":"Gece","url":"https://feeds.example.org/gece.xml"}}""")!!
        assertEquals(77L, hit.podcastIndexId)
        assertNull(PodcastIndexJson.byFeedUrl("""{"status":"true","feed":[]}"""))
        assertNull(PodcastIndexJson.byFeedUrl("not json"))
    }

    @Test
    fun itunesSearch() {
        val hits = ITunesJson.hits(
            """
            {"resultCount":4,"results":[
              {"collectionName":"Gece Yayını","artistName":"Gece Ekibi","feedUrl":"https://feeds.example.org/gece.xml",
               "artworkUrl60":"https://i.example.org/60.jpg","artworkUrl600":"https://i.example.org/600.jpg","trackCount":120},
              {"collectionName":"Adressiz (ücretli)","artistName":"Başkası","trackCount":10},
              {"trackName":"Yalnız iz","feedUrl":"https://feeds.example.org/iz.xml"},
              {"collectionName":"","feedUrl":"https://feeds.example.org/adsiz.xml"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("Gece Yayını", "Yalnız iz"), hits.map { it.title })
        assertEquals("https://i.example.org/600.jpg", hits[0].imageUrl)
        assertEquals("Gece Ekibi", hits[0].author)
        assertEquals(120, hits[0].episodeCount)
        assertEquals(HitSource.ITunes, hits[0].source)
        assertNull(hits[1].imageUrl)
        assertNull(hits[1].episodeCount)
        assertNull(hits[0].podcastIndexId)
    }

    // ------------------------------------------------------------ the search

    private fun hit(url: String, title: String = url, source: HitSource = HitSource.PodcastIndex, author: String? = null, image: String? = null, count: Int? = null, id: Long? = null) =
        PodcastHit(feedUrl = url, title = title, author = author, imageUrl = image, episodeCount = count, podcastIndexId = id, source = source)

    private class FakeDirectory(
        override val available: Boolean = true,
        private val result: () -> List<PodcastHit>,
    ) : PodcastDirectory {
        var asked = 0
        var lastTerm: String? = null

        override suspend fun search(term: String, limit: Int): List<PodcastHit> {
            asked++
            lastTerm = term
            return result()
        }
    }

    private fun many(count: Int, prefix: String = "https://pi.example.org/") = (0 until count).map { hit("$prefix$it.xml", "Show $it") }

    @Test
    fun podcastIndexFirstAndEnoughOfIt() = runBlocking {
        val primary = FakeDirectory { many(10) }
        val secondary = FakeDirectory { many(10, "https://it.example.org/") }
        val outcome = PodcastSearch(primary, secondary).search("gece")
        assertEquals(10, outcome.hits.size)
        assertNull(outcome.notice)
        assertEquals(0, secondary.asked)
        assertEquals("gece", primary.lastTerm)
    }

    @Test
    fun aThinAnswerIsFilledFromITunesWithoutDoubles() = runBlocking {
        val primary = FakeDirectory { listOf(hit("https://a.example.org/feed", "A", id = 5), hit("https://b.example.org/feed/", "B")) }
        val secondary = FakeDirectory {
            listOf(
                // the same feed with another scheme, case and a trailing slash: one show
                hit("http://A.example.org/feed/", "A (iTunes)", HitSource.ITunes, author = "Yazar", image = "https://i.example.org/a.jpg", count = 9),
                hit("https://c.example.org/feed", "C", HitSource.ITunes),
            )
        }
        val outcome = PodcastSearch(primary, secondary).search("x")
        assertNull(outcome.notice)
        assertEquals(listOf("A", "B", "C"), outcome.hits.map { it.title })
        val a = outcome.hits[0]
        // the first one stays, and takes what it lacked
        assertEquals("https://a.example.org/feed", a.feedUrl)
        assertEquals(HitSource.PodcastIndex, a.source)
        assertEquals("Yazar", a.author)
        assertEquals("https://i.example.org/a.jpg", a.imageUrl)
        assertEquals(9, a.episodeCount)
        assertEquals(5L, a.podcastIndexId)
    }

    @Test
    fun withoutKeysITunesAnswersAndTheSearchSaysSo() = runBlocking {
        val primary = FakeDirectory(available = false) { error("must not be asked") }
        val secondary = FakeDirectory { many(3, "https://it.example.org/") }
        val search = PodcastSearch(primary, secondary)
        assertFalse(search.primaryAvailable)
        val outcome = search.search("gece")
        assertEquals(3, outcome.hits.size)
        assertEquals(SearchNotice.PodcastIndexMissing, outcome.notice)
        assertEquals(0, primary.asked)
    }

    @Test
    fun podcastIndexFailingFallsBackToITunes() = runBlocking {
        val primary = FakeDirectory { throw DirectoryFailure.Server(401) }
        val secondary = FakeDirectory { many(2, "https://it.example.org/") }
        val outcome = PodcastSearch(primary, secondary).search("gece")
        assertEquals(2, outcome.hits.size)
        assertEquals(SearchNotice.PodcastIndexFailed, outcome.notice)
    }

    @Test
    fun itunesFailingAfterPodcastIndexAnsweredCostsOnlyTheExtras() = runBlocking {
        val primary = FakeDirectory { many(3) }
        val secondary = FakeDirectory { throw DirectoryFailure.Busy(5_000) }
        val outcome = PodcastSearch(primary, secondary).search("gece")
        assertEquals(3, outcome.hits.size)
    }

    @Test
    fun noDirectoryAnsweringIsAFailure() = runBlocking {
        val primary = FakeDirectory { throw DirectoryFailure.Offline() }
        val secondary = FakeDirectory { throw DirectoryFailure.Offline() }
        try {
            PodcastSearch(primary, secondary).search("gece")
            fail("answered")
        } catch (e: DirectoryFailure.Offline) {
            // the app says the network is down
        }
    }

    @Test
    fun aShowListedTwiceIsShownOnce() = runBlocking {
        // 1.0.1: the results' list keys a show by its feed address; Podcast Index lists one show under two ids
        val twice = many(9) + hit("https://pi.example.org/3.xml/", "Show 3 again", id = 77)
        val outcome = PodcastSearch(FakeDirectory { twice }, FakeDirectory { error("enough without iTunes") }).search("gece")
        assertEquals(9, outcome.hits.size)
        assertEquals(outcome.hits.size, outcome.hits.map { FeedUrl.key(it.feedUrl) }.toSet().size)
        assertEquals(77L, outcome.hits[3].podcastIndexId)
        // fewer than THIN once the doubles are gone: iTunes fills in, still each show once
        val thin = listOf(hit("https://a.example.org/feed", "A"), hit("https://A.example.org/feed/", "A again"))
        val filled = PodcastSearch(FakeDirectory { thin }, FakeDirectory { thin + many(1, "https://it.example.org/") }).search("x")
        assertEquals(listOf("A", "Show 0"), filled.hits.map { it.title })
    }

    @Test
    fun podcastIndexFindingNothingAsksITunes() = runBlocking {
        val primary = FakeDirectory { emptyList() }
        val secondary = FakeDirectory { many(1, "https://it.example.org/") }
        val outcome = PodcastSearch(primary, secondary).search("gece")
        assertEquals(1, outcome.hits.size)
        assertNull(outcome.notice)
    }
}
