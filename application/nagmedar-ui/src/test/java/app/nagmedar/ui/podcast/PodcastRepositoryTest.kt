package app.nagmedar.ui.podcast

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.NagmedarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/** S-1101, S-1102: subscribing, importing, exporting and refreshing, over a real (in memory) Room and a feed source that is a fake. */
@RunWith(AndroidJUnit4::class)
class PodcastRepositoryTest {

    private lateinit var db: NagmedarDatabase
    private var clock = 1_000_000L
    private val asked = mutableListOf<String>()
    private val conditions = mutableListOf<Pair<String?, String?>>()
    private var handler: (String) -> FeedFetch = { error("not expected: $it") }
    private var indexIds = mutableMapOf<String, Long>()

    private val source = object : FeedSource {
        override suspend fun fetch(url: String, etag: String?, lastModified: String?): FeedFetch {
            asked += url
            conditions += etag to lastModified
            return handler(url)
        }
    }

    private lateinit var repository: PodcastRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()
        repository = PodcastRepository(db.podcasts(), source, now = { clock }, io = Dispatchers.Unconfined, indexId = { indexIds[it] })
    }

    @After
    fun close() = db.close()

    private fun episode(guid: String, at: Long = 0) = ParsedEpisode(guid = guid, title = "Bölüm $guid", audioUrl = "https://example.org/$guid.mp3", publishedAt = at, durationMs = 60_000)

    private fun feed(title: String = "Gece Yayını", episodes: List<ParsedEpisode> = listOf(episode("1", 10), episode("2", 20)), author: String? = "Gece Ekibi", image: String? = "https://example.org/kapak.jpg") =
        ParsedFeed(title = title, author = author, imageUrl = image, link = "https://example.org", podcastGuid = "guid-1", fundingUrl = "https://example.org/destek", fundingMessage = "Destek", episodes = episodes)

    private fun fresh(parsed: ParsedFeed, url: String, permanent: String? = null, etag: String? = "\"v1\"") =
        FeedFetch.Fresh(parsed, finalUrl = permanent ?: url, permanentUrl = permanent, etag = etag, lastModified = "Sat, 10 Oct 2026 06:00:00 GMT")

    private fun serve(vararg pages: Pair<String, FeedFetch>) {
        val map = pages.toMap()
        handler = { url -> map[url] ?: throw FeedFailure.Server(404) }
    }

    @Test
    fun subscribingKeepsTheShowAndItsEpisodes() = runBlocking {
        serve("https://example.org/feed.xml" to fresh(feed(), "https://example.org/feed.xml"))
        val result = repository.subscribe("https://example.org/feed.xml") as SubscribeResult.Added
        assertEquals(2, result.episodes)
        val stored = db.podcasts().feedByUrl("https://example.org/feed.xml")!!
        assertEquals(result.feed.id, stored.id)
        assertEquals("Gece Yayını", stored.title)
        assertEquals("Gece Ekibi", stored.author)
        assertEquals("https://example.org/kapak.jpg", stored.imageUrl)
        assertEquals("guid-1", stored.podcastGuid)
        assertEquals("https://example.org/destek", stored.fundingUrl)
        assertEquals("\"v1\"", stored.etag)
        assertEquals(clock, stored.subscribedAt)
        assertEquals(listOf("Bölüm 2", "Bölüm 1"), db.podcasts().episodeList(stored.id).map { it.title })

        // what was there at the start is not new
        val summary = repository.subscriptions().first().single()
        assertEquals(2, summary.episodeCount)
        assertEquals(0, summary.newCount)
    }

    @Test
    fun anInvalidFeedSavesNothing() = runBlocking {
        handler = { throw FeedFailure.Invalid(InvalidReason.NotFeed) }
        val result = repository.subscribe("https://example.org/page.html") as SubscribeResult.Failed
        assertEquals(InvalidReason.NotFeed, (result.failure as FeedFailure.Invalid).reason)
        assertEquals(emptyList<Any>(), db.podcasts().feedList())
        // an invalid page is not tried again over http
        assertEquals(listOf("https://example.org/page.html"), asked)
    }

    @Test
    fun whatIsNoAddressIsNotAsked() = runBlocking {
        val result = repository.subscribe("hello world") as SubscribeResult.Failed
        assertTrue(result.failure is FeedFailure.BadAddress)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theSameFeedUnderAnotherSpellingIsAlready() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        repository.subscribe("https://example.org/feed")
        asked.clear()
        val again = repository.subscribe("feed://EXAMPLE.org/feed/") as SubscribeResult.Already
        assertEquals("https://example.org/feed", again.feed.feedUrl)
        assertTrue(asked.isEmpty())
        assertEquals(1, db.podcasts().feedList().size)
    }

    @Test
    fun aFeedThatMovedForGoodIsStoredWhereItIs() = runBlocking {
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/rss"))
        val added = repository.subscribe("https://old.example.org/feed") as SubscribeResult.Added
        assertEquals("https://new.example.org/rss", added.feed.feedUrl)
        assertEquals("https://new.example.org/rss", db.podcasts().feedList().single().feedUrl)
    }

    @Test
    fun theAddressAskedForStaysAnAliasOfTheOneItMovedTo() = runBlocking {
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/rss"))
        val added = repository.subscribe("https://old.example.org/feed") as SubscribeResult.Added
        assertEquals(listOf("new.example.org/rss", "old.example.org/feed"), added.feed.addressKeys())
        // a search result that still has the old address is told as subscribed, and subscribing again is Already
        assertTrue("old.example.org/feed" in PodcastScreenModel(repository.subscriptions().first()).subscribedKeys)
        asked.clear()
        val again = repository.subscribe("https://old.example.org/feed") as SubscribeResult.Already
        assertEquals(added.feed.id, again.feed.id)
        assertTrue(asked.isEmpty())
        assertEquals(1, db.podcasts().feedList().size)
    }

    @Test
    fun aFeedThatMovedOntoOneYouHaveIsRememberedUnderTheAddressAsked() = runBlocking {
        serve(
            "https://new.example.org/rss" to fresh(feed(), "https://new.example.org/rss"),
            "https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/rss"),
        )
        val first = (repository.subscribe("https://new.example.org/rss") as SubscribeResult.Added).feed
        val already = repository.subscribe("https://old.example.org/feed") as SubscribeResult.Already
        assertEquals(first.id, already.feed.id)
        assertEquals(listOf("new.example.org/rss", "old.example.org/feed"), db.podcasts().feed(first.id)!!.addressKeys())
        // the next time nothing is fetched at all
        asked.clear()
        assertTrue(repository.subscribe("https://old.example.org/feed") is SubscribeResult.Already)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun aFeedThatMovedToOneYouHaveIsAlready() = runBlocking {
        serve(
            "https://new.example.org/rss" to fresh(feed(), "https://new.example.org/rss"),
            "https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/rss"),
        )
        repository.subscribe("https://new.example.org/rss")
        val result = repository.subscribe("https://old.example.org/feed")
        assertTrue(result is SubscribeResult.Already)
        assertEquals(1, db.podcasts().feedList().size)
    }

    @Test
    fun withoutASchemeHttpsThenHttp() = runBlocking {
        handler = { url -> if (url.startsWith("https")) throw FeedFailure.Offline() else fresh(feed(), url) }
        val added = repository.subscribe("example.org/feed") as SubscribeResult.Added
        assertEquals("http://example.org/feed", added.feed.feedUrl)
        assertEquals(listOf("https://example.org/feed", "http://example.org/feed"), asked)
    }

    @Test
    fun offlineIsTheFailureWhenNothingAnswers() = runBlocking {
        handler = { throw FeedFailure.Offline() }
        val result = repository.subscribe("example.org/feed") as SubscribeResult.Failed
        assertTrue(result.failure is FeedFailure.Offline)
        assertEquals(2, asked.size)
    }

    @Test
    fun theSearchResultFillsWhatTheFeedLeavesOut() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(author = null, image = null), "https://example.org/feed"))
        val hit = PodcastHit("https://example.org/feed", "Gece", author = "Dizin Yazarı", imageUrl = "https://i.example.org/c.jpg", podcastIndexId = 920666)
        val stored = (repository.subscribe("https://example.org/feed", hit) as SubscribeResult.Added).feed
        assertEquals("Dizin Yazarı", stored.author)
        assertEquals("https://i.example.org/c.jpg", stored.imageUrl)
        assertEquals(920666L, stored.podcastIndexId)
        // the feed's own title is kept
        assertEquals("Gece Yayını", stored.title)
    }

    @Test
    fun anAddressTypedGetsItsPodcastIndexIdWhenKnown() = runBlocking {
        indexIds["https://example.org/feed"] = 42
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        assertEquals(42L, (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.podcastIndexId)
    }

    @Test
    fun anImportDoesNotAskPodcastIndexForEveryFeed() = runBlocking {
        val lookups = mutableListOf<String>()
        repository = PodcastRepository(db.podcasts(), source, now = { clock }, io = Dispatchers.Unconfined, indexId = { lookups += it; 7L })
        handler = { url -> fresh(feed(title = url), url) }
        repository.importFeeds((0 until 12).map { OpmlFeed("https://show$it.example.org/rss", "Yayın $it", null) })
        assertTrue(lookups.toString(), lookups.isEmpty())
        assertEquals(12, db.podcasts().feedList().size)
        // typed by hand, a feed is looked up
        repository.subscribe("https://typed.example.org/rss")
        assertEquals(listOf("https://typed.example.org/rss"), lookups)
    }

    @Test
    fun aSubscriptionCancelledWhileTheFeedIsReadSavesNothing() = runBlocking {
        val cancelling = object : FeedSource {
            override suspend fun fetch(url: String, etag: String?, lastModified: String?): FeedFetch {
                currentCoroutineContext().job.cancel()
                return fresh(feed(), url)
            }
        }
        val repository = PodcastRepository(db.podcasts(), cancelling, now = { clock }, io = Dispatchers.Unconfined)
        val job = launch(Dispatchers.Default) { repository.subscribe("https://example.org/feed") }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyList<Any>(), db.podcasts().feedList())
    }

    @Test
    fun aFeedWithNoTitleIsNamedByItsHost() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(title = ""), "https://example.org/feed"))
        assertEquals("example.org", (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.title)
    }

    @Test
    fun unsubscribingCanBeTakenBackWithTheListeningDone() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val added = repository.subscribe("https://example.org/feed") as SubscribeResult.Added
        val first = db.podcasts().episodeList(added.feed.id).first()
        repository.setPlayed(first.id, true)

        val gone = repository.unsubscribe(added.feed.id)!!
        assertEquals(2, gone.episodes.size)
        assertEquals(emptyList<Any>(), db.podcasts().feedList())
        assertNull(repository.unsubscribe(added.feed.id))

        val id = repository.resubscribe(gone)!!
        val back = db.podcasts().episodeList(id)
        assertEquals(2, back.size)
        assertEquals(listOf(true, false), back.map { it.played })
        assertEquals("Gece Yayını", db.podcasts().feed(id)!!.title)
    }

    @Test
    fun playedAndUnplayed() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id
        val one = db.podcasts().episodeList(id).first()
        repository.setPlayed(one.id, true)
        assertTrue(repository.episodes(id).first().first { it.id == one.id }.played)
        repository.setPlayed(one.id, false)
        assertFalse(repository.episodes(id).first().first { it.id == one.id }.played)
    }

    // ------------------------------------------------------------ OPML

    @Test
    fun anImportTellsWhatItDid() = runBlocking {
        serve(
            "https://good.example.org/rss" to fresh(feed(title = "İyi"), "https://good.example.org/rss"),
            "https://have.example.org/rss" to fresh(feed(title = "Zaten"), "https://have.example.org/rss"),
        )
        repository.subscribe("https://have.example.org/rss")
        asked.clear()
        val errors = mutableMapOf<String, FeedFailure>("https://bad.example.org/rss" to FeedFailure.Invalid(InvalidReason.NotFeed))
        val base = handler
        handler = { url -> errors[url]?.let { throw it } ?: base(url) }

        val progress = mutableListOf<Pair<Int, Int>>()
        val summary = repository.importFeeds(
            listOf(
                OpmlFeed("https://good.example.org/rss", "İyi", null),
                OpmlFeed("https://HAVE.example.org/rss/", "Zaten", null),
                OpmlFeed("https://bad.example.org/rss", "Bozuk", null),
                // the same feed once more: counted once
                OpmlFeed("http://good.example.org/rss", "İyi (http)", null),
            ),
        ) { done, total -> synchronized(progress) { progress += done to total } }

        assertEquals(1, summary.added)
        assertEquals(1, summary.already)
        assertEquals(listOf("Bozuk"), summary.failed.map { it.title })
        assertEquals(3, summary.total)
        assertFalse(summary.offline)
        // a feed already subscribed to is not fetched
        assertFalse("https://HAVE.example.org/rss/" in asked)
        assertTrue(progress.toString(), (3 to 3) in progress)
        assertEquals(2, db.podcasts().feedList().size)
        assertNull(db.podcasts().feedByUrl("https://bad.example.org/rss"))
    }

    @Test
    fun anImportWithTheNetworkDownSaysSo() = runBlocking {
        handler = { throw FeedFailure.Offline() }
        val summary = repository.importFeeds(listOf(OpmlFeed("https://a.example.org/rss", "A", null), OpmlFeed("https://b.example.org/rss", "B", null)))
        assertEquals(0, summary.added)
        assertEquals(2, summary.failed.size)
        assertTrue(summary.offline)
    }

    @Test
    fun aFileOfManyFeeds() = runBlocking {
        val list = (0 until 40).map { OpmlFeed("https://show$it.example.org/rss", "Yayın $it", null) }
        handler = { url -> fresh(feed(title = url), url) }
        val summary = repository.importFeeds(list)
        assertEquals(40, summary.added)
        assertEquals(40, db.podcasts().feedList().size)
    }

    @Test
    fun exportingGivesAFileOtherAppsAndWeRead() = runBlocking {
        serve(
            "https://a.example.org/rss" to fresh(feed(title = "Gece & Müzik"), "https://a.example.org/rss"),
            "https://b.example.org/rss" to fresh(feed(title = "Bilim"), "https://b.example.org/rss"),
        )
        repository.subscribe("https://a.example.org/rss")
        repository.subscribe("https://b.example.org/rss")
        val text = repository.exportOpml("Nagmedar")
        val list = Opml.parse(ByteArrayInputStream(text.toByteArray()))
        assertEquals("Nagmedar", list.title)
        assertEquals(listOf("Bilim", "Gece & Müzik"), list.feeds.map { it.title })
        assertEquals(listOf("https://b.example.org/rss", "https://a.example.org/rss"), list.feeds.map { it.feedUrl })
        assertEquals("https://example.org", list.feeds[0].htmlUrl)
    }

    @Test
    fun exportingNothingIsAnEmptyList() = runBlocking {
        val text = repository.exportOpml("Nagmedar")
        assertTrue(text, "<body>" in text && "<outline" !in text)
    }

    // ------------------------------------------------------------ refreshing

    @Test
    fun refreshingFindsTheNewEpisodes() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id

        clock += 60_000
        serve("https://example.org/feed" to fresh(feed(episodes = listOf(episode("3", 30), episode("2", 20), episode("1", 10))), "https://example.org/feed", etag = "\"v2\""))
        assertEquals(1, (repository.refresh(id) as RefreshResult.Updated).added)
        // sent what the feed gave last time
        assertEquals("\"v1\"" to "Sat, 10 Oct 2026 06:00:00 GMT", conditions.last())

        val summary = repository.subscriptions().first().single()
        assertEquals(3, summary.episodeCount)
        assertEquals(1, summary.newCount)
        assertEquals("\"v2\"", summary.feed.etag)
        assertEquals(clock, summary.feed.refreshedAt)

        // an episode played is no longer new
        repository.setPlayed(db.podcasts().episodeList(id).first { it.guid == "3" }.id, true)
        assertEquals(0, repository.subscriptions().first().single().newCount)
    }

    @Test
    fun refreshingKeepsTheListeningDone() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id
        db.podcasts().savePosition(db.podcasts().episodeList(id).first().id, positionMs = 30_000, played = true)
        repository.refresh(id)
        val again = db.podcasts().episodeList(id).first()
        assertEquals(30_000L, again.positionMs)
        assertTrue(again.played)
    }

    @Test
    fun nothingNewFromTheServer() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id
        clock += 1_000
        serve("https://example.org/feed" to FeedFetch.NotModified("https://example.org/feed", null))
        assertEquals(RefreshResult.NotModified, repository.refresh(id))
        assertEquals(clock, db.podcasts().feed(id)!!.refreshedAt)
    }

    @Test
    fun refreshingFollowsAFeedThatMoved() = runBlocking {
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed"))
        val id = (repository.subscribe("https://old.example.org/feed") as SubscribeResult.Added).feed.id
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/feed"))
        repository.refresh(id)
        assertEquals("https://new.example.org/feed", db.podcasts().feed(id)!!.feedUrl)
    }

    @Test
    fun refreshingKeepsTheOldAddressAsAnAlias() = runBlocking {
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed"))
        val id = (repository.subscribe("https://old.example.org/feed") as SubscribeResult.Added).feed.id
        serve("https://old.example.org/feed" to fresh(feed(), "https://old.example.org/feed", permanent = "https://new.example.org/feed"))
        repository.refresh(id)
        assertEquals(listOf("new.example.org/feed", "old.example.org/feed"), db.podcasts().feed(id)!!.addressKeys())
    }

    @Test
    fun aFeedThatMovedOntoAnotherSubscriptionStaysWhereItIsWhateverTheSpelling() = runBlocking {
        serve(
            "https://a.example.org/feed" to fresh(feed(title = "A"), "https://a.example.org/feed"),
            "https://b.example.org/feed" to fresh(feed(title = "B"), "https://b.example.org/feed"),
        )
        val a = (repository.subscribe("https://a.example.org/feed") as SubscribeResult.Added).feed.id
        repository.subscribe("https://b.example.org/feed")
        // B's address with another spelling (http, capitals, a trailing slash): the same by key, so not a move
        serve("https://a.example.org/feed" to fresh(feed(title = "A"), "https://a.example.org/feed", permanent = "http://B.example.org/feed/"))
        assertTrue(repository.refresh(a) is RefreshResult.Updated)
        assertEquals("https://a.example.org/feed", db.podcasts().feed(a)!!.feedUrl)
        assertEquals(2, db.podcasts().feedList().size)
    }

    @Test
    fun refreshingAFeedThatFails() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id
        handler = { throw FeedFailure.Server(410) }
        val result = repository.refresh(id) as RefreshResult.Failed
        assertEquals(410, (result.failure as FeedFailure.Server).status)
        // what was kept stays
        assertEquals(2, db.podcasts().episodeList(id).size)
        assertEquals(RefreshResult.Missing, repository.refresh(id + 100))
    }

    @Test
    fun theTitleOfARefreshedFeedIsTheFeedsNow() = runBlocking {
        serve("https://example.org/feed" to fresh(feed(), "https://example.org/feed"))
        val id = (repository.subscribe("https://example.org/feed") as SubscribeResult.Added).feed.id
        serve("https://example.org/feed" to fresh(feed(title = "Yeni Ad", image = null), "https://example.org/feed"))
        repository.refresh(id)
        val stored = db.podcasts().feed(id)!!
        assertEquals("Yeni Ad", stored.title)
        // a cover the feed stopped giving is kept
        assertNotNull(stored.imageUrl)
    }
}
