package app.nagmedar.macrobenchmark

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import androidx.media.utils.MediaConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 1.0.3: what Android Auto gets from the installed app, read the way Auto reads it: a MediaBrowserCompat bound to the
 * exported PlaybackService of the app named by "nagmedar.package" (the personal walk's app.nagmedar.debug; without
 * it the test does not run, so run.sh never reads a phone's own library). The root has exactly the four tabs, every
 * list under them answers, the Music's albums hold the walk's "Profile track" files ("nagmedar.tracks" of them), the
 * Podcasts' shows the feed the walk followed ("nagmedar.feed"), and the search finds a track. The personal walk runs
 * it (buildsystem/ci/emulator_smoke.py): the personal APK once gave Auto a root its own catalog did not know, which
 * no debug build showed. 1.0.3: it connects as an Auto that shows custom browse actions, and the root offers "Abone
 * ol"; Keşfet's lists answer (Apple's shows, or the one line that says the directory cannot be reached). The Radio
 * tab's seven lists answer (the radio directory's countries, languages, categories, most listened and most liked
 * too), a country opens on its stations, and every station offers "Favorilere ekle" or "Favorilerden çıkar".
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val target = arguments.getString("nagmedar.package")?.takeIf { it.isNotBlank() }
    private lateinit var browser: MediaBrowserCompat

    @Before
    fun connect() {
        val app = target
        assumeTrue("no nagmedar.package to read Android Auto's tree from", app != null)
        val done = CountDownLatch(1)
        val refused = AtomicBoolean(false)
        instrumentation.runOnMainSync {
            browser = MediaBrowserCompat(
                instrumentation.targetContext,
                ComponentName(app!!, SERVICE),
                object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() = done.countDown()

                    override fun onConnectionFailed() {
                        refused.set(true)
                        done.countDown()
                    }
                },
                // as Android Auto connects: it shows custom browse actions (androidx.media 1.7)
                Bundle().apply { putInt(MediaConstants.BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT, BROWSE_ACTIONS) },
            )
            browser.connect()
        }
        assertTrue("no answer from $app's $SERVICE within ${WAIT_MS / 1000} s", done.await(WAIT_MS, TimeUnit.MILLISECONDS))
        assertFalse("$app's $SERVICE refused the connection (is $app installed?)", refused.get())
    }

    @After
    fun disconnect() {
        if (::browser.isInitialized) instrumentation.runOnMainSync { browser.disconnect() }
    }

    @Test
    fun aCarSeesTheFourTabsAndTheirLists() {
        val root = browser.root
        assertEquals("the root Android Auto is given", ROOT, root)
        val tabs = children(root)
        assertEquals(
            "the root's entries, Auto's tabs: ${describe(tabs)}",
            listOf("$root/music", "$root/podcasts", "$root/radio", "$root/recent"),
            tabs.map { it.mediaId },
        )
        assertTrue("every tab opens: ${describe(tabs)}", tabs.all { it.isBrowsable && !it.isPlayable && !it.description.title.isNullOrBlank() })

        browseActions()
        music("$root/music")
        podcasts("$root/podcasts")
        radio("$root/radio")
        assertAnswers(tabs.last())
        found()
    }

    /** The lists of the tab [id] (its browsable entries), at least [least] of them. */
    private fun lists(id: String, least: Int): List<MediaItem> {
        val entries = children(id)
        val lists = entries.filter { it.isBrowsable }
        assertTrue("the lists of $id: ${describe(entries)}", lists.size >= least)
        return lists
    }

    /**
     * The Music's lists answer; the albums of the walk's files hold all of its tracks, each playable (read again while
     * the medialibrary is still giving them, for [MUSIC_WAIT_MS] at most).
     */
    private fun music(id: String) {
        lists(id, 4).forEach { list -> assertAnswers(list) }
        val expected = arguments.getString("nagmedar.tracks")?.toIntOrNull() ?: return
        // the walk's music fills the groupings (playlists it has none of)
        for (grouping in listOf("albums", "artists", "years", "folders")) {
            val entries = musicChildren("$id/$grouping")
            assertTrue("$grouping: ${describe(entries)}", entries.isNotEmpty() && entries.none { it.isNothing })
        }
        val deadline = System.currentTimeMillis() + MUSIC_WAIT_MS
        while (true) {
            val albums = musicChildren("$id/albums").filter { it.title.startsWith(WALK_ALBUM) }
            val tracks = albums.flatMap { album -> musicChildren(album.mediaId!!) }.filter { it.title.startsWith(WALK_TRACK) }
            if (tracks.size == expected || System.currentTimeMillis() > deadline) {
                assertTrue("the walk's albums under Albums: ${describe(albums)}", albums.isNotEmpty() && albums.all { it.isBrowsable })
                assertEquals("the walk's tracks in its albums", expected, tracks.size)
                assertTrue("every track plays", tracks.all { it.isPlayable })
                return
            }
            Thread.sleep(PAUSE_MS)
        }
    }

    /**
     * 1.0.3: the root offers Auto its custom browse actions, each with a label and an icon: "Abone ol", "Favorilere ekle"
     * and "Favorilerden çıkar".
     */
    @Suppress("DEPRECATION") // the typed getter needs API 33; the test runs on older ones too
    private fun browseActions() {
        val actions = browser.extras?.getParcelableArrayList<Bundle>(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST).orEmpty()
        for (id in listOf(SUBSCRIBE, FAVOURITE, UNFAVOURITE)) {
            val action = actions.firstOrNull { it.getString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID) == id }
            assertNotNull("$id among the root's browse actions: ${actions.map { it.getString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID) }}", action)
            assertFalse("$id's label", action!!.getString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL).isNullOrBlank())
            assertTrue("$id's icon", action.getString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI).orEmpty().startsWith("android.resource://"))
        }
    }

    /**
     * 1.0.3: the Radio's seven lists answer (stations, or the one line that says why there are none: nothing kept yet,
     * the directory cannot be reached); the first country opens on its stations; every station offers "Favorilere ekle"
     * or "Favorilerden çıkar".
     */
    private fun radio(id: String) {
        lists(id, 7).forEach { list -> stationsOffer(list, assertAnswers(list)) }
        val country = children("$id/countries", NETWORK_WAIT_MS).firstOrNull { it.isBrowsable } ?: return
        stationsOffer(country, assertAnswers(country))
    }

    /** The stations among [entries] of [list] each offer a favourite's browse action. */
    private fun stationsOffer(list: MediaItem, entries: List<MediaItem>) {
        entries.filter { it.isPlayable && !it.isNothing }.forEach { station ->
            val actions = station.description.extras?.getStringArrayList(MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST).orEmpty()
            assertTrue("${station.title} in ${list.title}: $actions", actions.any { it == FAVOURITE || it == UNFAVOURITE })
        }
    }

    /**
     * The Podcasts' lists answer; with the walk's feed followed, its show is under the shows with playable episodes.
     * 1.0.3: Keşfet's lists answer too: the popular shows and the categories (Apple's, or the line that it cannot be
     * reached), the countries (no network needed).
     */
    private fun podcasts(id: String) {
        lists(id, 4).forEach { list -> assertAnswers(list) }
        lists("$id/discover", 3).forEach { list -> assertAnswers(list) }
        if (arguments.getString("nagmedar.feed").isNullOrBlank()) return
        val shows = children("$id/shows").filter { it.isBrowsable }
        assertTrue("the followed podcast under the shows: ${describe(children("$id/shows"))}", shows.isNotEmpty())
        val episodes = children(shows.first().mediaId!!)
        assertTrue("its episodes play: ${describe(episodes)}", episodes.isNotEmpty() && episodes.all { it.isPlayable && !it.isNothing })
    }

    /** Android Auto's search finds one of the walk's tracks by its title. */
    private fun found() {
        if (arguments.getString("nagmedar.tracks").isNullOrBlank()) return
        val title = "$WALK_TRACK 1.1"
        val deadline = System.currentTimeMillis() + MUSIC_WAIT_MS
        while (true) {
            val items = search(title)
            if (items.any { it.title == title && it.isPlayable }) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("the search for \"$title\" found: ${describe(items)}")
            Thread.sleep(PAUSE_MS)
        }
    }

    /**
     * A list answers: entries, or one line that says why it has none (an empty list's, an offline directory's), given
     * [NETWORK_WAIT_MS] (a directory's list asks its servers in turn). Returns them.
     */
    private fun assertAnswers(list: MediaItem): List<MediaItem> {
        val entries = children(list.mediaId!!, NETWORK_WAIT_MS)
        assertTrue("${list.title} (${list.mediaId}) gave Auto nothing", entries.isNotEmpty())
        if (entries.any { it.isNothing }) assertEquals("${list.title}: ${describe(entries)}", 1, entries.size)
        return entries
    }

    /**
     * The entries of a music list once the medialibrary gave the music: while it does not, the list is the one line that
     * says so, and is asked again.
     */
    private fun musicChildren(id: String): List<MediaItem> {
        val deadline = System.currentTimeMillis() + MUSIC_WAIT_MS
        while (true) {
            val entries = children(id)
            val waiting = entries.singleOrNull()?.mediaId?.endsWith(MUSIC_UNAVAILABLE) == true
            if (!waiting || System.currentTimeMillis() > deadline) return entries
            Thread.sleep(PAUSE_MS)
        }
    }

    /** The entries under [id] as Auto's subscription gets them, given [waitMs]. */
    private fun children(id: String, waitMs: Long = WAIT_MS): List<MediaItem> {
        val done = CountDownLatch(1)
        val answer = AtomicReference<List<MediaItem>>()
        val callback = object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaItem>) {
                answer.compareAndSet(null, children.toList())
                done.countDown()
            }

            override fun onError(parentId: String) = done.countDown()
        }
        instrumentation.runOnMainSync { browser.subscribe(id, callback) }
        val answered = done.await(waitMs, TimeUnit.MILLISECONDS)
        instrumentation.runOnMainSync { browser.unsubscribe(id, callback) }
        assertTrue("no answer for $id within ${waitMs / 1000} s", answered)
        val entries = answer.get()
        assertNotNull("the service gave an error for $id", entries)
        return entries
    }

    private fun search(query: String): List<MediaItem> {
        val done = CountDownLatch(1)
        val answer = AtomicReference<List<MediaItem>>(emptyList())
        instrumentation.runOnMainSync {
            browser.search(query, null, object : MediaBrowserCompat.SearchCallback() {
                override fun onSearchResult(query: String, extras: Bundle?, items: List<MediaItem>) {
                    answer.set(items.toList())
                    done.countDown()
                }

                override fun onError(query: String, extras: Bundle?) = done.countDown()
            })
        }
        assertTrue("no answer to the search for \"$query\" within ${WAIT_MS / 1000} s", done.await(WAIT_MS, TimeUnit.MILLISECONDS))
        return answer.get()
    }

    private val MediaItem.title: String get() = description.title?.toString().orEmpty()

    /** A line that plays nothing: an empty list's, an offline directory's, music not given in time. */
    private val MediaItem.isNothing: Boolean get() = mediaId?.startsWith(NOTHING) == true

    private fun describe(items: List<MediaItem>) = items.take(12).joinToString { "${it.title} [${it.mediaId}]" } +
        if (items.size > 12) " … (${items.size})" else ""

    private companion object {
        const val SERVICE = "org.videolan.vlc.PlaybackService"

        /**
         * The custom browse actions a car shows on an entry, and Nağmedar's (CarActions): "Abone ol", "Favorilere ekle",
         * "Favorilerden çıkar".
         */
        const val BROWSE_ACTIONS = 4
        const val SUBSCRIBE = "app.nagmedar.auto.SUBSCRIBE"
        const val FAVOURITE = "app.nagmedar.auto.FAVOURITE"
        const val UNFAVOURITE = "app.nagmedar.auto.UNFAVOURITE"

        /** The browser's root in every build (MediaSessionBrowser.ID_ROOT). */
        const val ROOT = "//app.nagmedar/r"
        const val NOTHING = "$ROOT/error/media"
        const val MUSIC_UNAVAILABLE = "?t=MusicUnavailable"

        /** The walk's music (emulator.yml): "Profile track a.t" in "Profile album a". */
        const val WALK_TRACK = "Profile track"
        const val WALK_ALBUM = "Profile album"

        const val WAIT_MS = 20_000L

        /** A list of a directory (the radio's, the podcasts'): its servers are looked up and asked in turn. */
        const val NETWORK_WAIT_MS = 60_000L
        const val MUSIC_WAIT_MS = 30_000L
        const val PAUSE_MS = 1_000L
    }
}
