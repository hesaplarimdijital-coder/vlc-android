package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.components.UndoBarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.io.File

/** S-1101, S-1102: the Podcasts screen's state holder over a real (in memory) Room and fakes for the network. */
@RunWith(AndroidJUnit4::class)
class PodcastControllerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: NagmedarDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var background: CoroutineScope
    private val imports = ImportTracker()
    private lateinit var context: Context
    private lateinit var controller: PodcastController
    private lateinit var follower: Job
    private val undo = UndoBarState()
    private val played = mutableListOf<String>()

    private var feeds = mutableMapOf<String, () -> FeedFetch>()
    private var primary: () -> List<PodcastHit> = { emptyList() }
    private var secondary: () -> List<PodcastHit> = { emptyList() }
    private var primaryAvailable = true

    private val source = object : FeedSource {
        override suspend fun fetch(url: String, etag: String?, lastModified: String?): FeedFetch =
            feeds[url]?.invoke() ?: throw FeedFailure.Server(404)
    }

    private fun directory(available: () -> Boolean, hits: () -> List<PodcastHit>) = object : PodcastDirectory {
        override val available: Boolean get() = available()
        override suspend fun search(term: String, limit: Int): List<PodcastHit> = hits()
    }

    @Before
    fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        background = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = PodcastRepository(db.podcasts(), source, io = Dispatchers.Unconfined)
        val search = PodcastSearch(directory({ primaryAvailable }) { primary() }, directory({ true }) { secondary() })
        controller = PodcastController(repository, search, scope, context, undo, background, imports) { feed, episode -> played += "${feed.title}/${episode.title}" }
        follower = scope.launch { controller.follow() }
    }

    @After
    fun close() {
        // the flows over Room end before the database does: nothing runs on after the test
        scope.cancel()
        background.cancel()
        runBlocking { follower.join() }
        db.close()
    }

    private fun eventually(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out: $what")
            Thread.sleep(10)
        }
    }

    private fun episode(guid: String, at: Long) = ParsedEpisode(guid, "Bölüm $guid", "https://example.org/$guid.mp3", publishedAt = at)

    private fun serve(url: String, title: String = "Gece Yayını") {
        feeds[url] = { FeedFetch.Fresh(ParsedFeed(title = title, episodes = listOf(episode("1", 10), episode("2", 20))), url, null, null, null) }
    }

    private fun hit(url: String, title: String = "Gece Yayını") = PodcastHit(url, title, "Gece Ekibi", episodeCount = 2)

    @Test
    fun oneTapOnAResultSubscribes() {
        serve("https://example.org/feed")
        val result = hit("https://example.org/feed")
        controller.subscribe(result)
        eventually("subscribed") { FeedUrl.key(result.feedUrl) in controller.model.subscribedKeys }
        assertTrue(controller.model.subscribing.isEmpty())
        assertEquals(1, db.podcasts().feedList().size)
        // "Gece Yayını eklendi · Geri al"
        assertEquals(1, undo.messages.size)
        undo.messages.single().onUndo()
        eventually("taken back") { controller.model.subscriptions?.isEmpty() == true }
    }

    @Test
    fun aRefusedFeedWarnsAndSavesNothing() {
        feeds["https://example.org/page"] = { throw FeedFailure.Invalid(InvalidReason.NotFeed) }
        val result = hit("https://example.org/page", "Sayfa")
        controller.subscribe(result)
        eventually("refused") { controller.model.subscribing[FeedUrl.key(result.feedUrl)] is SubscribeUi.Failed }
        assertTrue(db.podcasts().feedList().isEmpty())
        assertTrue(undo.messages.isEmpty())
        // trying again with a feed that now works
        serve("https://example.org/page")
        controller.subscribe(result)
        eventually("subscribed") { FeedUrl.key(result.feedUrl) in controller.model.subscribedKeys }
        assertNull(controller.model.subscribing[FeedUrl.key(result.feedUrl)])
    }

    @Test
    fun searchingTellsWhereTheResultsCameFrom() {
        primaryAvailable = false
        secondary = { listOf(hit("https://example.org/a"), hit("https://example.org/b", "Başka")) }
        controller.onQueryChange("gece")
        controller.runSearch()
        val found = controller.model.search as SearchUi.Found
        assertEquals("gece", found.term)
        assertEquals(2, found.hits.size)
        assertEquals(SearchNotice.PodcastIndexMissing, found.notice)

        controller.clearSearch()
        assertEquals(SearchUi.Idle, controller.model.search)
        assertEquals("", controller.model.query)
    }

    @Test
    fun aSearchWithNoNetworkSaysOffline() {
        primary = { throw DirectoryFailure.Offline() }
        secondary = { throw DirectoryFailure.Offline() }
        controller.onQueryChange("gece")
        controller.runSearch()
        assertEquals(SearchFailure.Offline, (controller.model.search as SearchUi.Failed).reason)
        // emptying the field leaves the failure
        controller.onQueryChange("")
        assertEquals(SearchUi.Idle, controller.model.search)
    }

    @Test
    fun anAddressTypedInTheSearchIsAdded() {
        serve("https://example.org/feed")
        controller.onQueryChange("https://example.org/feed")
        controller.runSearch()
        eventually("opened") { controller.model.page != null }
        assertNull(controller.model.add)
        assertEquals("Gece Yayını", controller.model.page!!.summary.feed.title)
        eventually("episodes") { controller.model.page?.episodes?.size == 2 }
    }

    @Test
    fun theAddWindowStaysWithTheReasonAndOpensTheNewPage() {
        controller.openAdd()
        assertEquals(AddUi(), controller.model.add)
        feeds["https://example.org/html"] = { throw FeedFailure.Invalid(InvalidReason.NotFeed) }
        controller.submitAddress("https://example.org/html")
        val refused = controller.model.add!!
        assertFalse(refused.working)
        assertEquals(InvalidReason.NotFeed, (refused.failure as FeedFailure.Invalid).reason)

        serve("https://example.org/html")
        controller.submitAddress("https://example.org/html")
        eventually("closed") { controller.model.add == null && controller.model.page != null }

        controller.dismissAdd()
        controller.submitAddress("not an address")
        assertTrue(controller.model.add!!.failure is FeedFailure.BadAddress)
        controller.dismissAdd()
        assertNull(controller.model.add)
    }

    @Test
    fun unsubscribingIsTakenBackWithItsEpisodes() {
        serve("https://example.org/feed")
        controller.submitAddress("https://example.org/feed")
        eventually("page") { controller.model.page?.episodes?.size == 2 }
        val id = controller.model.page!!.summary.feed.id
        undo.messages.clear()

        controller.unsubscribe(id)
        eventually("gone") { controller.model.subscriptions?.isEmpty() == true }
        assertNull(controller.model.page)
        assertEquals(1, undo.messages.size)

        undo.messages.single().onUndo()
        eventually("back") { controller.model.subscriptions?.size == 1 }
        assertEquals(2, controller.model.subscriptions!!.single().episodeCount)
    }

    @Test
    fun anEpisodePlaysAndIsMarkedPlayed() {
        serve("https://example.org/feed")
        controller.submitAddress("https://example.org/feed")
        eventually("episodes") { controller.model.page?.episodes?.size == 2 }
        val feed: PodcastFeed = controller.model.page!!.summary.feed
        val first: PodcastEpisode = controller.model.page!!.episodes!!.first()
        controller.play(feed, first)
        assertEquals(listOf("Gece Yayını/Bölüm 2"), played)

        controller.setPlayed(first.id, true)
        eventually("played") { controller.model.page?.episodes?.first { it.id == first.id }?.played == true }
    }

    // ------------------------------------------------------------ OPML

    private fun file(name: String, text: String): Uri = Uri.fromFile(folder.newFile(name).also { it.writeText(text) })

    @Test
    fun anOpmlFileIsImportedAndSummed() {
        serve("https://a.example.org/rss", "A")
        serve("https://b.example.org/rss", "B")
        feeds["https://c.example.org/rss"] = { throw FeedFailure.Invalid(InvalidReason.NoEpisodes) }
        val opml = Opml.write(
            listOf(OpmlFeed("https://a.example.org/rss", "A"), OpmlFeed("https://b.example.org/rss", "B"), OpmlFeed("https://c.example.org/rss", "C")),
            "Test", 0,
        )
        controller.importFile(file("list.opml", opml))
        eventually("done") { controller.model.importing is ImportUi.Done }
        val summary = (controller.model.importing as ImportUi.Done).summary
        assertEquals(2, summary.added)
        assertEquals(listOf("C"), summary.failed.map { it.title })
        eventually("listed") { controller.model.subscriptions?.size == 2 }

        // again: both are subscribed to already
        controller.dismissImport()
        eventually("dismissed") { controller.model.importing == null }
        controller.importFile(file("again.opml", opml))
        eventually("done again") { controller.model.importing is ImportUi.Done }
        val second = (controller.model.importing as ImportUi.Done).summary
        assertEquals(0, second.added)
        assertEquals(2, second.already)
    }

    @Test
    fun aFileThatIsNoOpmlIsSaidSo() {
        controller.importFile(file("page.html", "<html><body>hello</body></html>"))
        eventually("unusable") { controller.model.importing is ImportUi.Unusable }
        assertEquals(OpmlProblem.NotOpml, (controller.model.importing as ImportUi.Unusable).problem)
        assertTrue(db.podcasts().feedList().isEmpty())
    }

    @Test
    fun aFileThatIsGoneIsUnreadable() {
        controller.importFile(Uri.fromFile(File(folder.root, "missing.opml")))
        eventually("unusable") { controller.model.importing is ImportUi.Unusable }
        assertNull((controller.model.importing as ImportUi.Unusable).problem)
    }

    @Test
    fun subscriptionsAreExportedToTheChosenFile() {
        serve("https://a.example.org/rss", "A")
        controller.subscribe(hit("https://a.example.org/rss", "A"))
        eventually("subscribed") { controller.model.subscriptions?.size == 1 }
        val target = folder.newFile("out.opml")
        controller.export(Uri.fromFile(target))
        eventually("written") { target.length() > 0 }
        val list = Opml.parse(target.inputStream())
        assertEquals(listOf("https://a.example.org/rss"), list.feeds.map { it.feedUrl })
        assertNotNull(list.title)
    }

    @Test
    fun anExportToAFileThatCannotBeWrittenSaysSoAndLeavesNothing() {
        serve("https://a.example.org/rss", "A")
        controller.subscribe(hit("https://a.example.org/rss", "A"))
        eventually("subscribed") { controller.model.subscriptions?.size == 1 }
        // a provider that is not there: the export fails the way a provider that refuses the mode does, and does not crash
        controller.export(Uri.parse("content://app.nagmedar.no.such.provider/document/1"))
        eventually("said") {
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() == context.getString(app.nagmedar.ui.R.string.nag_pod_export_failed)
        }
    }

    @Test
    fun theUndoStillWorksWhenTheScreenHasBeenLeft() {
        serve("https://a.example.org/rss", "A")
        controller.subscribe(hit("https://a.example.org/rss", "A"))
        eventually("subscribed") { controller.model.subscriptions?.size == 1 }
        // the screen's scope goes away with the screen; the undo bar belongs to the shell and stays
        scope.cancel()
        undo.messages.single().onUndo()
        eventually("taken back") { db.podcasts().feedList().isEmpty() }
    }

    @Test
    fun anUnsubscribingUndoneAfterTheScreenWasLeftBringsEverythingBack() {
        serve("https://a.example.org/rss", "A")
        controller.subscribe(hit("https://a.example.org/rss", "A"))
        eventually("subscribed") { controller.model.subscriptions?.size == 1 }
        val id = db.podcasts().feedList().single().id
        val episode = db.podcasts().episodeList(id).first()
        db.podcasts().setPlayed(episode.id, true)
        undo.messages.clear()
        controller.unsubscribe(id)
        eventually("away") { db.podcasts().feedList().isEmpty() }
        scope.cancel()
        undo.messages.single().onUndo()
        eventually("back") { db.podcasts().feedList().size == 1 }
        val back = db.podcasts().feedList().single()
        assertTrue(db.podcasts().episodeList(back.id).single { it.guid == episode.guid }.played)
    }

    @Test
    fun anImportGoesOnAndKeepsItsSummaryWhenTheScreenIsLeft() {
        serve("https://a.example.org/rss", "A")
        serve("https://b.example.org/rss", "B")
        val file = file("two.opml", """<opml version="2.0"><body><outline type="rss" text="A" xmlUrl="https://a.example.org/rss"/><outline type="rss" text="B" xmlUrl="https://b.example.org/rss"/></body></opml>""")
        controller.importFile(file)
        scope.cancel()
        eventually("done") { imports.state.value is ImportUi.Done }
        assertEquals(2, (imports.state.value as ImportUi.Done).summary.added)
        assertEquals(2, db.podcasts().feedList().size)
    }

    @Test
    fun aResultReachedByAnOldAddressIsSubscribedAndOpensItsPage() {
        feeds["https://old.example.org/feed"] = {
            FeedFetch.Fresh(ParsedFeed(title = "Gece", episodes = listOf(episode("1", 10))), "https://new.example.org/rss", "https://new.example.org/rss", null, null)
        }
        val result = hit("https://old.example.org/feed", "Gece")
        controller.subscribe(result)
        eventually("subscribed") { FeedUrl.key(result.feedUrl) in controller.model.subscribedKeys }
        assertEquals("https://new.example.org/rss", db.podcasts().feedList().single().feedUrl)
        // the row opens the page, and a second tap on "subscribe" says it is already there and opens it too
        controller.openSubscribed(result)
        assertNotNull(controller.model.page)
        controller.closePage()
        assertNull(controller.model.page)
        controller.subscribe(result)
        eventually("opened") { controller.model.page != null }
        assertEquals(1, db.podcasts().feedList().size)
        assertNull(controller.model.subscribing[FeedUrl.key(result.feedUrl)])
    }
}
