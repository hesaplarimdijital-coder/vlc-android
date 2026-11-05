package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.0.3: Android Auto's Keşfet: Apple's chart of popular shows read, the lookup that gives their feeds, the two joined,
 * the addresses asked, and the order of the episodes as they were last listened to.
 */
@RunWith(AndroidJUnit4::class)
class PodcastChartsTest {

    /** Apple's "Top Shows" as rss.marketingtools.apple.com gives it (shortened). */
    private val chart = """
        {"feed":{"title":"Top Shows","country":"tr","results":[
          {"artistName":"Podcast BPT","id":"1549686147","name":"Ortamlarda Satılacak Bilgi","kind":"podcasts",
           "artworkUrl100":"https://is1-ssl.mzstatic.com/a/100x100bb.png",
           "genres":[{"genreId":"1324","name":"Society &amp; Culture","url":"https://itunes.apple.com/tr/genre/id1324"},
                     {"genreId":"26","name":"Podcasts"}],
           "url":"https://podcasts.apple.com/tr/podcast/id1549686147"},
          {"artistName":"Karnaval","id":"1585156719","name":"Meksika Açmazı","kind":"podcasts",
           "genres":[{"genreId":"1303","name":"Comedy"},{"genreId":"1303","name":"Comedy"}]},
          {"artistName":"Apple","id":"999","name":"Yalnız Apple'da","genres":[]},
          {"id":"not-a-number","name":"Bozuk"},
          {"id":"1000","name":""}
        ]}}
    """.trimIndent()

    @Test
    fun theChartIsReadInItsOrder() {
        val entries = AppleChartJson.entries(chart)
        assertEquals(listOf(1549686147L, 1585156719L, 999L), entries.map { it.id })
        assertEquals("Ortamlarda Satılacak Bilgi", entries[0].name)
        assertEquals("Podcast BPT", entries[0].artist)
        assertEquals("https://is1-ssl.mzstatic.com/a/100x100bb.png", entries[0].artworkUrl)
        // the root "Podcasts" is no category; the entity is read; a category named twice is one
        assertEquals(listOf(ChartGenre(1324, "Society & Culture")), entries[0].genres)
        assertEquals(listOf(ChartGenre(1303, "Comedy")), entries[1].genres)
        assertEquals(emptyList<ChartGenre>(), entries[2].genres)
        assertEquals(null, entries[1].artworkUrl)
    }

    @Test
    fun aChartWithNothingOrNonsense() {
        assertEquals(emptyList<ChartEntry>(), AppleChartJson.entries("""{"feed":{"title":"Top Shows"}}"""))
        assertEquals(emptyList<ChartEntry>(), AppleChartJson.entries("{}"))
        try {
            AppleChartJson.entries("<html>bakım</html>")
            fail("not JSON")
        } catch (e: DirectoryFailure.Server) {
            assertEquals(200, e.status)
        }
    }

    @Test
    fun theLookupGivesTheFeedsByAppleId() {
        val found = ITunesJson.lookup(
            """
            {"resultCount":3,"results":[
              {"collectionId":1549686147,"collectionName":"Ortamlarda Satılacak Bilgi","artistName":"Podcast BPT",
               "feedUrl":"https://anchor.fm/s/44a38a74/podcast/rss","artworkUrl600":"https://is1-ssl.mzstatic.com/a/600x600bb.jpg","trackCount":404},
              {"collectionId":999,"collectionName":"Yalnız Apple'da","artistName":"Apple"},
              {"collectionName":"Kimliksiz","feedUrl":"https://example.org/x.xml"}
            ]}
            """.trimIndent(),
        )
        assertEquals(setOf(1549686147L), found.keys)
        val hit = found.getValue(1549686147L)
        assertEquals("https://anchor.fm/s/44a38a74/podcast/rss", hit.feedUrl)
        assertEquals("https://is1-ssl.mzstatic.com/a/600x600bb.jpg", hit.imageUrl)
        assertEquals(404, hit.episodeCount)
        assertEquals(HitSource.ITunes, hit.source)
    }

    @Test
    fun theChartsShowsAreThoseWithAFeedEachOnce() {
        val entries = AppleChartJson.entries(chart)
        val feed = PodcastHit("https://anchor.fm/s/44a38a74/podcast/rss", "Ortamlarda Satılacak Bilgi", source = HitSource.ITunes)
        val same = PodcastHit("https://ANCHOR.fm/s/44a38a74/podcast/rss/", "Aynı program, başka kimlik", "Biri", "https://i.example.org/c.jpg")
        val shows = ApplePodcasts.shows(entries, mapOf(1549686147L to feed, 1585156719L to same))
        // the second is the first's feed under another spelling: shown once; the one with no feed is left out
        assertEquals(1, shows.size)
        // what the lookup lacks comes from the chart
        assertEquals("Podcast BPT", shows[0].hit.author)
        assertEquals("https://is1-ssl.mzstatic.com/a/100x100bb.png", shows[0].hit.imageUrl)
        assertEquals(listOf(ChartGenre(1324, "Society & Culture")), shows[0].genres)
    }

    @Test
    fun theAddressesAsked() {
        assertEquals(
            "https://rss.marketingtools.apple.com/api/v2/tr/podcasts/top/100/podcasts.json",
            ITunesDirectory.chartUrl(ITunesDirectory.CHART_URL, "tr", 100).toString(),
        )
        assertEquals(
            "https://itunes.apple.com/lookup?id=1,22,333&entity=podcast&country=TR",
            ITunesDirectory.lookupUrl(ITunesDirectory.LOOKUP_URL, listOf(1, 22, 333), "tr").toString(),
        )
    }

    @Test
    fun aStorefrontIsTwoLettersInLowerCase() {
        assertEquals("tr", ApplePodcasts.storefront(" TR "))
        assertEquals(null, ApplePodcasts.storefront("tur"))
        assertEquals(null, ApplePodcasts.storefront("t1"))
        assertEquals(null, ApplePodcasts.storefront(null))
        assertTrue(ApplePodcasts.COUNTRIES.all { ApplePodcasts.storefront(it) == it })
        assertEquals(ApplePodcasts.COUNTRIES.distinct(), ApplePodcasts.COUNTRIES)
        assertTrue("tr" in ApplePodcasts.COUNTRIES && "us" in ApplePodcasts.COUNTRIES)
    }

    @Test
    fun theEpisodeListenedToLastGoesFirst() {
        var order = emptyList<Long>()
        listOf(3L, 5L, 3L, 7L).forEach { order = ListenedOrder.heard(order, it) }
        assertEquals(listOf(7L, 3L, 5L), order)
        assertEquals(order, ListenedOrder.read(ListenedOrder.write(order)))
        // what is no id is left out
        assertEquals(listOf(4L, 9L), ListenedOrder.read("4,x,,-2,9,4"))
        assertEquals(emptyList<Long>(), ListenedOrder.read(null))
        // the order is kept short
        val long = (1L..300L).fold(emptyList<Long>()) { kept, id -> ListenedOrder.heard(kept, id) }
        assertEquals(ListenedOrder.MAX, long.size)
        assertEquals(300L, long.first())
    }
}
