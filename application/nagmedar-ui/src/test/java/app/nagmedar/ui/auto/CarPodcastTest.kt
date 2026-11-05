package app.nagmedar.ui.auto

import app.nagmedar.ui.podcast.ApplePodcasts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import org.videolan.vlc.media.MediaSessionBrowser

/**
 * 1.0.3: Android Auto's Podcast tab: the episodes left in the middle in the order they were last listened to, Keşfet
 * (Apple's popular shows of the device's country, its categories, the other countries), a show not followed opened on
 * its feed's episodes under the row "Abone ol" (and its browse action), and the directory's new shows in the search.
 */
class CarPodcastTest {

    private fun tree(data: FakeCarData, page: Int = CarTree.PAGE) = CarTree(data, FakeCarLook, page)

    private fun children(data: FakeCarData, id: String, page: Int = CarTree.PAGE): List<CarItem> =
        runBlocking { tree(data, page).children(id) } ?: error("no children of $id")

    private fun ids(items: List<CarItem>) = items.map { it.id }

    private fun titles(items: List<CarItem>) = items.map { it.title }

    private val gece = hit(1, "Gece Yarısı Sohbetleri")
    private val bilim = hit(2, "Bilim Saati", author = null)
    private val komik = hit(3, "Kahkaha Kuşağı")

    private val chart = listOf(
        chartShow(gece, 1324 to "Society & Culture", 1304 to "Education"),
        chartShow(bilim, 1533 to "Science", 1304 to "Education"),
        chartShow(komik, 1303 to "Comedy"),
    )

    @Test
    fun thePodcastTabEndsWithDiscover() {
        val data = FakeCarData()
        val tab = children(data, CarIds.PODCASTS)
        assertEquals(listOf(CarIds.CONTINUE, CarIds.SHOWS, CarIds.NEW, CarIds.DISCOVER), ids(tab))
        assertEquals(listOf("Continue", "Subscriptions", "NewEpisodes", "Discover"), titles(tab))
        assertTrue(tab.all { it.browsable && !it.playable })
        assertEquals("icon:Discover", tab.last().iconUri)
        // Keşfet's own lists: the device's popular shows (tiles), its categories, the countries
        val discover = children(data, CarIds.DISCOVER)
        assertEquals(listOf(CarIds.chart("tr"), CarIds.CATEGORIES, CarIds.COUNTRIES), ids(discover))
        assertEquals(listOf("PopularShows", "Categories", "Countries"), titles(discover))
        assertEquals(listOf(CarStyle.Grid, CarStyle.List, CarStyle.List), discover.map { it.children })
        // opening Keşfet asks the directory nothing
        assertTrue(data.chartsAsked.isEmpty())
    }

    @Test
    fun thePopularShowsAreTheDeviceCountrysChartInItsOrder() {
        val followed = feed(7, "Bilim Saati (abone)").copy(feedUrl = bilim.feedUrl)
        val data = FakeCarData(charts = mapOf("tr" to chart), feeds = listOf(followed))
        val shows = children(data, CarIds.chart("tr"))
        assertEquals(listOf("tr"), data.chartsAsked)
        assertEquals(listOf("Gece Yarısı Sohbetleri", "Bilim Saati (abone)", "Kahkaha Kuşağı"), titles(shows))
        assertTrue(shows.all { it.browsable && !it.playable && it.children == CarStyle.List })
        // a show not followed offers "Abone ol"; one followed says so instead
        assertEquals(listOf(CarActions.SUBSCRIBE), shows[0].actions)
        assertEquals("Yapımcı 1", shows[0].subtitle)
        assertEquals("web:https://example.com/art/1.jpg", shows[0].iconUri)
        assertTrue(shows[1].actions.isEmpty())
        assertEquals("Subscribed", shows[1].subtitle)
        assertEquals(CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl), shows[0].id)
    }

    @Test
    fun theCategoriesAreThoseOfTheChartsShowsTheFullestFirst() {
        val data = FakeCarData(charts = mapOf("tr" to chart))
        val categories = children(data, CarIds.CATEGORIES)
        // Education has two shows; the others one each, by name
        assertEquals(listOf("Education", "Comedy", "Science", "Society & Culture"), titles(categories))
        assertEquals("2 Shows", categories[0].subtitle)
        assertEquals(CarIds.category("tr", 1304), categories[0].id)
        assertEquals("web:https://example.com/art/1.jpg", categories[0].iconUri)
        assertTrue(categories.all { it.browsable && it.children == CarStyle.Grid })
        // a category opens on its shows in the chart's order
        assertEquals(listOf("Gece Yarısı Sohbetleri", "Bilim Saati"), titles(children(data, categories[0].id)))
        assertEquals(listOf("Kahkaha Kuşağı"), titles(children(data, CarIds.category("tr", 1303))))
        // a category the chart no longer has says so
        assertEquals(listOf(CarIds.nothing("Empty")), ids(children(data, CarIds.category("tr", 1545))))
    }

    @Test
    fun theCountriesStartWithTheDevicesThenByName() {
        val data = FakeCarData(country = "de", charts = mapOf("us" to listOf(chartShow(komik, 1303 to "Comedy"))))
        val countries = children(data, CarIds.COUNTRIES)
        assertEquals(ApplePodcasts.COUNTRIES.size, countries.size)
        assertEquals(CarIds.chart("de"), countries.first().id)
        assertEquals("country:de", countries.first().title)
        val rest = titles(countries.drop(1))
        assertEquals(rest.sorted(), rest)
        assertFalse("country:de" in rest)
        assertTrue(countries.all { it.browsable && it.children == CarStyle.Grid })
        // a country opens on its own chart
        assertEquals(listOf("Kahkaha Kuşağı"), titles(children(data, CarIds.chart("us"))))
        assertEquals(listOf("us"), data.chartsAsked)
    }

    @Test
    fun anUnreachableDirectorySaysSoInOneLine() {
        val data = FakeCarData(podcastsOffline = true)
        for (id in listOf(CarIds.chart("tr"), CarIds.CATEGORIES, CarIds.category("tr", 1303))) {
            assertEquals(id, listOf(CarIds.nothing("PodcastsOffline")), ids(children(data, id)))
        }
        assertTrue(MediaSessionBrowser.isNoMedia(CarIds.nothing("PodcastsOffline")))
        // the countries need no network
        assertEquals(ApplePodcasts.COUNTRIES.size, children(data, CarIds.COUNTRIES).size)
    }

    @Test
    fun aShowNotFollowedOpensOnAbonaOlThenItsFeedsEpisodes() {
        val data = FakeCarData(feedsRead = mapOf(gece.feedUrl to parsedFeed("Gece", episodes = 3)))
        val show = CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl)
        val entries = children(data, show)
        assertEquals(listOf("Subscribe", "Gece 3", "Gece 2", "Gece 1"), titles(entries))
        val subscribe = entries.first()
        assertTrue(subscribe.playable && !subscribe.browsable)
        assertEquals("Gece", subscribe.subtitle)
        assertEquals("icon:Subscribe", subscribe.iconUri)
        assertEquals(CarIds.subscribe(gece.feedUrl, gece.title, gece.author, gece.imageUrl), subscribe.id)
        // the episodes play: from the feed, from their start
        val episodes = entries.drop(1)
        assertTrue(episodes.all { it.playable && !it.browsable })
        assertEquals("day:3000000", episodes[0].subtitle)
        assertEquals("web:https://example.com/feed-art.jpg", episodes[0].iconUri)
        val play = runBlocking { tree(data).play(episodes[1].id) } as CarPlay.Episode
        assertEquals("Gece 2", play.episode.title)
        assertEquals("https://example.com/audio/2.mp3", play.episode.audioUrl)
        assertEquals(0L, play.episode.positionMs)
        assertEquals(gece.feedUrl, play.feed.feedUrl)
        assertEquals("Gece", play.feed.title)
        // the row "Abone ol" plays nothing: it subscribes to the show as the directory gave it
        val subscribing = runBlocking { tree(data).play(subscribe.id) } as CarPlay.Subscribe
        assertEquals(gece, subscribing.show)
    }

    @Test
    fun aLongFeedIsCutAndAnUnreadableOneSaysSo() {
        val data = FakeCarData(feedsRead = mapOf(gece.feedUrl to parsedFeed("Gece", episodes = 9)))
        val show = CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl)
        // "Abone ol" and the newest episodes, one page at most
        assertEquals(4, children(data, show, page = 4).size)
        val broken = CarIds.directoryShow(bilim.feedUrl, bilim.title, null, null)
        assertEquals(listOf(CarIds.nothing("ShowUnavailable")), ids(children(data, broken)))
        assertNull(runBlocking { tree(data).play(CarIds.directoryEpisode(bilim.feedUrl, "g1")) })
    }

    @Test
    fun aShowFollowedMeanwhileOpensAndPlaysAsItsSubscription() {
        val followed = feed(7, "Gece Yarısı Sohbetleri").copy(feedUrl = gece.feedUrl)
        val kept = episode(70, 7, "Gece 2", positionMs = 600_000).copy(guid = "g2")
        val data = FakeCarData(feeds = listOf(followed), episodes = listOf(kept), feedsRead = mapOf(gece.feedUrl to parsedFeed("Gece")))
        val show = CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl)
        // no "Abone ol" any more: its episodes as Programlarım lists them, the feed not read
        assertEquals(listOf(CarIds.episode(70)), ids(children(data, show)))
        assertTrue(data.feedsAsked.isEmpty())
        // an episode of the feed's list plays the kept one, where it was left
        val play = runBlocking { tree(data).play(CarIds.directoryEpisode(gece.feedUrl, "g2")) } as CarPlay.Episode
        assertEquals(70L, play.episode.id)
        assertEquals(600_000L, play.episode.positionMs)
        assertEquals(7L, play.feed.id)
    }

    @Test
    fun aBrowseActionOrTheRowNameTheShowToSubscribeTo() {
        val data = FakeCarData()
        val tree = tree(data)
        val show = CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl)
        assertEquals(gece, tree.subscription(show))
        assertEquals(gece, tree.subscription(CarIds.subscribe(gece.feedUrl, gece.title, gece.author, gece.imageUrl)))
        // a show with no author or cover keeps none
        assertEquals(bilim.copy(imageUrl = null), tree.subscription(CarIds.directoryShow(bilim.feedUrl, bilim.title, null, null)))
        // any other entry subscribes to nothing
        assertNull(tree.subscription(CarIds.show(1)))
        assertNull(tree.subscription(CarIds.SHOWS))
        assertNull(tree.subscription("//other.app/root"))
    }

    @Test
    fun aShowReadAgainAfterSubscribingSaysItIsFollowed() {
        val data = FakeCarData()
        val show = CarIds.directoryShow(gece.feedUrl, gece.title, gece.author, gece.imageUrl)
        val before = runBlocking { tree(data).item(show) }!!
        assertEquals(listOf(CarActions.SUBSCRIBE), before.actions)
        assertEquals("Yapımcı 1", before.subtitle)
        // subscribed (under another spelling of its address, as a feed that moved keeps it)
        data.feeds = listOf(feed(8, "Gece Yarısı Sohbetleri").copy(feedUrl = "https://example.com/feed/1.xml/"))
        val after = runBlocking { tree(data).item(show) }!!
        assertTrue(after.actions.isEmpty())
        assertEquals("Subscribed", after.subtitle)
        assertEquals(show, after.id)
        // a followed show reads as Programlarım lists it; an entry of no kind has nothing to read
        assertEquals(CarIds.show(8), runBlocking { tree(data).item(CarIds.show(8)) }!!.id)
        assertNull(runBlocking { tree(data).item(CarIds.SHOWS) })
    }

    @Test
    fun theSearchListsTheDirectorysNewShowsUnderTheirOwnHeading() {
        val followed = feed(1, "Gece Notları").copy(feedUrl = "https://example.com/feed/9.xml")
        val data = FakeCarData(
            feeds = listOf(followed),
            shows = listOf(hit(9, "Gece Notları"), gece, hit(4, "Gece Kuşu")),
        )
        val found = runBlocking { tree(data).search("gece") }
        // the show followed under Programlar; the directory's others under "Yeni podcastler", offering "Abone ol"
        assertEquals(listOf("FoundShows", "FoundNewShows", "FoundNewShows"), found.map { it.group })
        assertEquals(listOf("Gece Notları", "Gece Yarısı Sohbetleri", "Gece Kuşu"), titles(found))
        assertTrue(found.drop(1).all { it.browsable && it.actions == listOf(CarActions.SUBSCRIBE) })
        // an unreachable directory leaves the rest of the search as it was
        data.podcastsOffline = true
        assertEquals(listOf("FoundShows"), runBlocking { tree(data).search("gece") }.map { it.group })
    }

    @Test
    fun theEpisodesLeftInTheMiddleFollowTheOrderTheyWereLastListenedTo() {
        val feeds = listOf(feed(1, "Fularsız Entellik"))
        val episodes = listOf(
            episode(10, 1, "Eski", positionMs = 600_000),
            episode(11, 1, "Dün", positionMs = 600_000),
            episode(12, 1, "Bugün", positionMs = 600_000),
            episode(13, 1, "Hiç notlanmamış", positionMs = 600_000, published = 99_000_000),
        )
        // the history has the first listening's order (10, 11, 12); the app noted 11 taken up again after 12
        val data = FakeCarData(feeds = feeds, episodes = episodes, listened = listOf(12, 11, 10), heard = listOf(11, 12))
        assertEquals(listOf(CarIds.episode(11), CarIds.episode(12), CarIds.episode(10), CarIds.episode(13)), ids(children(data, CarIds.CONTINUE)))
        // with nothing noted (before 1.0.3) the history's order, then the newest
        data.heard = emptyList()
        assertEquals(listOf(CarIds.episode(12), CarIds.episode(11), CarIds.episode(10), CarIds.episode(13)), ids(children(data, CarIds.CONTINUE)))
    }

    @Test
    fun theIdsOfDiscoverComeBack() {
        val url = "https://feeds.example.org/a b?x=1&y=2"
        val show = CarIds.parse(CarIds.directoryShow(url, "A & B?", "Yazar=1", "https://i.example.org/c.jpg?s=600"))!!
        assertEquals(CarKind.DirectoryShow, show.kind)
        assertEquals(url, show.key)
        assertEquals("A & B?", show.params["n"])
        assertEquals("Yazar=1", show.params["a"])
        assertEquals("https://i.example.org/c.jpg?s=600", show.params["l"])
        val episode = CarIds.parse(CarIds.directoryEpisode(url, "guid/1?&"))!!
        assertEquals(CarKind.DirectoryEpisode, episode.kind)
        assertEquals("guid/1?&", episode.key)
        assertEquals(url, episode.params["u"])
        val category = CarIds.parse(CarIds.category("tr", 1303))!!
        assertEquals(CarKind.Category, category.kind)
        assertEquals(1303L, category.number)
        assertEquals("tr", category.params["c"])
        assertEquals(CarKind.Chart, CarIds.parse(CarIds.chart("tr"))!!.kind)
        assertEquals(CarKind.Subscribe, CarIds.parse(CarIds.subscribe(url, "A", null, null))!!.kind)
        // the lists of Keşfet are no keyed entries
        assertNull(CarIds.parse(CarIds.DISCOVER)!!.kind)
        assertNull(CarIds.parse(CarIds.CATEGORIES)!!.kind)
        assertNull(CarIds.parse(CarIds.COUNTRIES)!!.kind)
        // a chart of no storefront is nothing
        assertEquals(emptyList<CarItem>(), children(FakeCarData(), CarIds.chart("x1")))
    }
}
