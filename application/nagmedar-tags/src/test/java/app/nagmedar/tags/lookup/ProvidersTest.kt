/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: from Metamatik (the tests of lookup/musicbrainz, lookup/deezer, lookup/itunes), a fork of Metadator, under the AGPL-3.0; asked through a fake
 * WebGetter instead of Ktor's MockEngine.
 */
package app.nagmedar.tags.lookup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.3.0: the catalogues' answers read as Metamatik read them. */
@RunWith(AndroidJUnit4::class)
class ProvidersTest {

    /** The parameters of [url], decoded. */
    private fun params(url: String): Map<String, String> =
        url.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private val musicBrainz = """
        {"recordings":[{"id":"rec-1","score":100,"title":"Bohemian Rhapsody","length":354000,
          "first-release-date":"1975-10-31",
          "artist-credit":[{"name":"Queen","joinphrase":"","artist":{"id":"art-1","name":"Queen"}}],
          "releases":[
            {"id":"rel-comp","title":"Greatest Hits","status":"Official","date":"1981",
             "release-group":{"id":"rg-2","primary-type":"Album","secondary-types":["Compilation"]},
             "media":[{"position":1,"track-count":17,"track":[{"id":"t-2","number":"1"}]}]},
            {"id":"rel-1","title":"A Night at the Opera","status":"Official","date":"1975-11-21",
             "release-group":{"id":"rg-1","primary-type":"Album"},
             "media":[{"position":1,"format":"CD","track-count":12,"track":[{"id":"t-1","number":"11"}]}]}
          ],
          "tags":[{"count":5,"name":"progressive rock"}]}]}
    """.trimIndent()

    @Test
    fun musicBrainzGivesACandidateAReleaseTheStudioAlbumFirst() = runTest {
        var asked = ""
        val provider = MusicBrainzProvider({ url ->
            asked = params(url)["query"].orEmpty()
            WebReply(200, musicBrainz)
        })
        val result = provider.search(LookupQuery("Bohemian Rhapsody", "Queen", durationMs = 354_000)) as LookupResult.Success
        assertEquals("recording:\"Bohemian Rhapsody\" AND artist:\"Queen\"", asked)
        assertEquals(2, result.candidates.size)
        val best = result.candidates.first { it.album == "A Night at the Opera" }
        assertEquals(11, best.trackNumber)
        assertEquals(12, best.trackTotal)
        assertEquals("1975-11-21", best.date)
        assertEquals(listOf("Progressive Rock"), best.genres)
        assertEquals("https://coverartarchive.org/release-group/rg-1/front-1200", best.artworkUrl)
        assertEquals("rec-1", best.identifiers["MUSICBRAINZ_TRACKID"])
        assertEquals("art-1", best.identifiers["MUSICBRAINZ_ARTISTID"])
        assertTrue(result.candidates.all { it.score > 0.9 })
    }

    @Test
    fun musicBrainzQuotesAreEscapedAndABusyServiceIsRateLimiting() = runTest {
        assertEquals("recording:\"Say \\\"Hi\\\"\"", MusicBrainzProvider.buildLuceneQuery(LookupQuery("Say \"Hi\"")))
        assertEquals(LookupResult.RateLimited, MusicBrainzProvider({ WebReply(503, "") }).search(LookupQuery("x")))
    }

    @Test
    fun musicBrainzNamesTheCoverOfAReleaseAFileIsTaggedAs() {
        val covers = MusicBrainzProvider({ WebReply(200, "{}") }).artworkFor(
            mapOf("MUSICBRAINZ_ALBUMID" to "0a8e8d55-4b83-4f8a-9732-fbb5ded9f344", "MUSICBRAINZ_RELEASEGROUPID" to "not an id"),
        )
        assertEquals(listOf("https://coverartarchive.org/release/0a8e8d55-4b83-4f8a-9732-fbb5ded9f344/front-1200"), covers.map { it.url })
    }

    @Test
    fun deezerResultsAreCompletedWithTheirTracksDetails() = runTest {
        val provider = DeezerProvider { url ->
            when {
                url.startsWith("https://api.deezer.com/search") -> WebReply(
                    200,
                    """{"data":[{"id":7,"title":"Halo","duration":261,"artist":{"name":"Beyoncé"},
                       "album":{"id":1,"title":"I Am... Sasha Fierce","cover_xl":"xl","cover_medium":"md"}}]}""",
                )
                url == "https://api.deezer.com/track/7" -> WebReply(
                    200,
                    """{"id":7,"title":"Halo","duration":261,"isrc":"USSM10804556","track_position":2,
                       "disk_number":1,"release_date":"2008-11-14","artist":{"name":"Beyoncé"},
                       "contributors":[{"name":"Beyoncé","role":"Main"}],
                       "album":{"id":1,"title":"I Am... Sasha Fierce"}}""",
                )
                else -> WebReply(404, "")
            }
        }
        val halo = (provider.search(LookupQuery("Halo", "Beyoncé")) as LookupResult.Success).candidates.single()
        assertEquals(2, halo.trackNumber)
        assertEquals("USSM10804556", halo.isrc)
        assertEquals("2008-11-14", halo.date)
        assertEquals("xl", halo.artworkUrl)
        assertEquals(261_000L, halo.durationMs)
    }

    @Test
    fun deezersQuotaIsRateLimiting() = runTest {
        val provider = DeezerProvider { WebReply(200, """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""") }
        assertEquals(LookupResult.RateLimited, provider.search(LookupQuery("x")))
    }

    @Test
    fun iTunesSearchesTheCountrysStoreElseTheDefaultOne() = runTest {
        val asked = mutableListOf<String?>()
        val provider = ITunesProvider({ url ->
            val country = params(url)["country"]
            asked += country
            if (country == "XK") {
                WebReply(400, "")
            } else {
                WebReply(
                    200,
                    """{"resultCount":2,"results":[
                       {"wrapperType":"track","kind":"song","trackId":1,"trackName":"Gülümse","artistName":"Sezen Aksu",
                        "collectionName":"Gülümse","artworkUrl100":"https://is1.mzstatic.com/image/thumb/a/100x100bb.jpg",
                        "releaseDate":"1991-01-01T08:00:00Z","trackNumber":1,"trackCount":10,"discNumber":1,"discCount":1,
                        "trackTimeMillis":245000,"primaryGenreName":"Pop"},
                       {"wrapperType":"collection","collectionId":9}]}""",
                )
            }
        }, country = { "XK" })
        val result = provider.search(LookupQuery("Gülümse", "Sezen Aksu")) as LookupResult.Success
        assertEquals(listOf("XK", null), asked)
        val song = result.candidates.single()
        assertEquals("1991-01-01", song.date)
        assertEquals("https://is1.mzstatic.com/image/thumb/a/1500x1500bb.jpg", song.artworkUrl)
        assertEquals("https://is1.mzstatic.com/image/thumb/a/250x250bb.jpg", song.artworkThumbnailUrl)
        assertEquals(listOf("Pop"), song.genres)
        assertEquals("Sezen Aksu Gülümse", ITunesProvider.buildTerm(LookupQuery("Gülümse", "Sezen Aksu")))
    }

    @Test
    fun noNetworkIsOffline() = runTest {
        val offline = WebGetter { throw IOException("no route") }
        assertEquals(LookupResult.Offline, ITunesProvider(offline, country = { "TR" }).search(LookupQuery("x")))
        assertEquals(LookupResult.Offline, DeezerProvider(offline).search(LookupQuery("x")))
        assertEquals(LookupResult.Offline, MusicBrainzProvider(offline).search(LookupQuery("x")))
        assertEquals(LookupResult.RateLimited, ITunesProvider({ WebReply(403, "") }, country = { "TR" }).search(LookupQuery("x")))
    }
}
