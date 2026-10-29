package app.nagmedar.ui.auto

import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.radio.DirectoryTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import org.videolan.vlc.media.MediaSessionBrowser

/** 1.0: Android Auto's browse tree: its four tabs, each list's entries for no, some and many items, and what an entry plays. */
class CarTreeTest {

    private val gulumse = listOf(track(1, "Gülümse"), track(2, "Hadi Bakalım"), track(3, "Ben Şimdi"))
    private val tarkan = listOf(
        track(4, "Şımarık", artist = "Tarkan", album = "Ölürüm Sana", year = 1997, number = 1),
        track(5, "Şıkıdım", artist = "Tarkan", album = "Aacayipsin", year = 1994, number = 1),
        track(6, "Kuzu Kuzu", artist = "Tarkan", album = "Karma", year = 2001, number = 1),
    )
    private val undated = track(7, "Eski Kayıt", artist = "Zeki Müren", album = null, year = null)

    private fun tree(data: FakeCarData, page: Int = CarTree.PAGE) = CarTree(data, FakeCarLook, page)

    private fun children(data: FakeCarData, id: String, page: Int = CarTree.PAGE): List<CarItem> =
        runBlocking { tree(data, page).children(id) } ?: error("no children of $id")

    private fun ids(items: List<CarItem>) = items.map { it.id }

    private fun titles(items: List<CarItem>) = items.map { it.title }

    @Test
    fun theRootHasExactlyTheFourTabsInOrder() {
        val root = children(FakeCarData(), MediaSessionBrowser.ID_ROOT)
        assertEquals(MediaSessionBrowser.ROOT_TABS, ids(root))
        assertEquals(listOf(CarIds.MUSIC, CarIds.PODCASTS, CarIds.RADIO, CarIds.RECENT), ids(root))
        assertEquals(listOf("Music", "Podcasts", "Radio", "Recent"), titles(root))
        assertTrue(root.all { it.browsable && !it.playable && it.iconUri != null })
        assertEquals(listOf(CarStyle.Category, CarStyle.Category, CarStyle.Category, CarStyle.List), root.map { it.children })
    }

    @Test
    fun eachTabHasItsLists() {
        val data = FakeCarData()
        assertEquals(listOf(CarIds.ALBUMS, CarIds.ARTISTS, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS), ids(children(data, CarIds.MUSIC)))
        // grid for albums, artists, shows and stations; rows for tracks and episodes
        assertEquals(listOf(CarStyle.Grid, CarStyle.Grid, CarStyle.Grid, CarStyle.List, CarStyle.List), children(data, CarIds.MUSIC).map { it.children })
        assertEquals(listOf(CarIds.CONTINUE, CarIds.SHOWS, CarIds.NEW), ids(children(data, CarIds.PODCASTS)))
        assertEquals(listOf(CarStyle.List, CarStyle.Grid, CarStyle.List), children(data, CarIds.PODCASTS).map { it.children })
        assertEquals(listOf(CarIds.FAVOURITES, CarIds.STATIONS_RECENT, CarIds.TAGS), ids(children(data, CarIds.RADIO)))
        assertEquals(listOf(CarStyle.Grid, CarStyle.Grid, CarStyle.List), children(data, CarIds.RADIO).map { it.children })
    }

    @Test
    fun anIdOfAnotherTreeIsNotTheCatalogs() {
        assertNull(runBlocking { tree(FakeCarData()).children("//other.app/root") })
        assertNull(runBlocking { tree(FakeCarData()).play("//other.app/root/1") })
    }

    @Test
    fun anEmptyListSaysSoInOneLineThatPlaysNothing() {
        val data = FakeCarData()
        for (id in listOf(CarIds.ALBUMS, CarIds.ARTISTS, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.RECENT, CarIds.CONTINUE, CarIds.SHOWS, CarIds.NEW, CarIds.FAVOURITES, CarIds.STATIONS_RECENT, CarIds.TAGS)) {
            val list = children(data, id)
            assertEquals(id, listOf(CarIds.nothing("Empty")), ids(list))
            assertEquals(id, "Empty", list.single().title)
            assertTrue(MediaSessionBrowser.isNoMedia(list.single().id))
            assertNull(runBlocking { tree(data).play(list.single().id) })
            assertNull(runBlocking { tree(data).play(CarIds.NOTHING) })
        }
    }

    @Test
    fun albumsAreTilesThatOpenOnTheirTracks() {
        val data = FakeCarData(tracks = gulumse + tarkan)
        val albums = children(data, CarIds.ALBUMS)
        // as the Albums view sorts them: by artist, then by year
        assertEquals(listOf("Gülümse", "Aacayipsin", "Ölürüm Sana", "Karma"), titles(albums))
        val gulumseAlbum = albums.first { it.title == "Gülümse" }
        assertEquals("Sezen Aksu", gulumseAlbum.subtitle)
        assertEquals("cover:file:///music/1.flac", gulumseAlbum.iconUri)
        assertTrue(gulumseAlbum.browsable && !gulumseAlbum.playable)
        assertEquals(CarStyle.List, gulumseAlbum.children)

        val tracks = children(data, gulumseAlbum.id)
        assertEquals(listOf("Gülümse", "Hadi Bakalım", "Ben Şimdi"), titles(tracks))
        assertTrue(tracks.all { it.playable && !it.browsable })
        // the second track plays the album from there, named as the album
        val play = runBlocking { tree(data).play(tracks[1].id) } as CarPlay.Tracks
        assertEquals(1, play.start)
        assertEquals(listOf(1L, 2L, 3L), play.tracks.map { it.id })
        assertEquals("Hadi Bakalım", play.tracks[play.start].title)
        assertEquals(PlaySource(PlayFrom.Album, "Gülümse"), play.source)
    }

    @Test
    fun anArtistOpensOnTheirAlbums() {
        val data = FakeCarData(tracks = gulumse + tarkan)
        val artists = children(data, CarIds.ARTISTS)
        assertEquals(setOf("Sezen Aksu", "Tarkan"), titles(artists).toSet())
        val tarkanArtist = artists.first { it.title == "Tarkan" }
        assertEquals("3 Albums", tarkanArtist.subtitle)
        assertEquals(CarStyle.Grid, tarkanArtist.children)
        assertEquals(setOf("Ölürüm Sana", "Aacayipsin", "Karma"), titles(children(data, tarkanArtist.id)).toSet())
    }

    @Test
    fun aLongListIsCutIntoPages() {
        val many = (1L..7L).map { track(it, "Parça $it", album = "Albüm $it", artist = "Sanatçı $it") }
        val data = FakeCarData(tracks = many)
        val pages = children(data, CarIds.ALBUMS, page = 3)
        assertEquals(listOf(CarIds.page(CarIds.ALBUMS, 0), CarIds.page(CarIds.ALBUMS, 1), CarIds.page(CarIds.ALBUMS, 2)), ids(pages))
        assertTrue(pages.all { it.browsable })
        // a page is a tile with its first album's cover, and its albums stay tiles
        assertTrue(pages.all { it.children == CarStyle.Grid })
        assertEquals("cover:file:///music/1.flac", pages[0].iconUri)
        assertTrue(pages[0].title.contains(" – "))
        assertEquals(3, children(data, pages[0].id, page = 3).size)
        assertEquals(1, children(data, pages[2].id, page = 3).size)
        // all of them once, over the pages
        val shown = pages.flatMap { children(data, it.id, page = 3) }
        assertEquals(7, shown.map { it.id }.toSet().size)
    }

    @Test
    fun aLongTrackListIsCutIntoPagesThatPlayTheWholeList() {
        val many = (1L..5L).map { track(it, "Parça $it", album = "Uzun Albüm", number = it.toInt()) }
        val data = FakeCarData(recent = many)
        val pages = children(data, CarIds.RECENT, page = 2)
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.children == CarStyle.List && it.iconUri != null })
        assertEquals("cover:file:///music/3.flac", pages[1].iconUri)
        val second = children(data, pages[1].id, page = 2)
        assertEquals(listOf("Parça 3", "Parça 4"), titles(second))
        // a track of the second page plays the whole list from it
        val play = runBlocking { tree(data, 2).play(second[1].id) } as CarPlay.Tracks
        assertEquals(5, play.tracks.size)
        assertEquals(3, play.start)
    }

    @Test
    fun theYearsAreDecadesThenYearsThenTracks() {
        val data = FakeCarData(tracks = gulumse + tarkan + undated)
        val decades = children(data, CarIds.YEARS)
        assertEquals(listOf(CarIds.decade(2000), CarIds.decade(1990), CarIds.year(null)), ids(decades))
        assertEquals(listOf("2000s", "1990s", "UnknownYear"), titles(decades))
        assertEquals("5 Tracks", decades[1].subtitle)
        val years = children(data, CarIds.decade(1990))
        assertEquals(listOf("1997", "1994", "1991"), titles(years))
        assertEquals(listOf("Gülümse", "Hadi Bakalım", "Ben Şimdi").sorted(), titles(children(data, CarIds.year(1991))).sorted())
        assertEquals(listOf("Eski Kayıt"), titles(children(data, CarIds.year(null))))
        val play = runBlocking { tree(data).play(CarIds.track(CarIds.year(null), 0)) } as CarPlay.Tracks
        assertEquals(listOf(7L), play.tracks.map { it.id })
    }

    @Test
    fun theTurkishDecadesTakeTheirEnding() {
        assertTrue(CarDecades.frontVowel(2000))
        assertTrue(CarDecades.frontVowel(1980))
        assertTrue(CarDecades.frontVowel(1970))
        assertTrue(CarDecades.frontVowel(2020))
        assertTrue(CarDecades.frontVowel(1950))
        assertFalse(CarDecades.frontVowel(1990))
        assertFalse(CarDecades.frontVowel(2010))
        assertFalse(CarDecades.frontVowel(1960))
        assertFalse(CarDecades.frontVowel(1940))
        assertFalse(CarDecades.frontVowel(1930))
    }

    @Test
    fun theFoldersAreTheMusicSectionsFolders() {
        val data = FakeCarData(tracks = gulumse + tarkan)
        val folders = children(data, CarIds.FOLDERS)
        assertEquals(setOf("Sezen Aksu", "Tarkan"), titles(folders).toSet())
        assertTrue(folders.all { it.subtitle == "/storage/emulated/0/Music" })
        assertEquals(3, children(data, folders.first { it.title == "Tarkan" }.id).size)
    }

    @Test
    fun playlistsOpenOnTheirTracksAndPlayAsTheList() {
        val list = PlaylistSummary(9, "Yol şarkıları", 2, 400_000)
        val data = FakeCarData(playlists = mapOf(list to listOf(tarkan[0], gulumse[0])))
        val playlists = children(data, CarIds.PLAYLISTS)
        assertEquals(listOf(CarIds.playlist(9)), ids(playlists))
        assertEquals("2 Tracks", playlists.single().subtitle)
        assertEquals("playlist:9", playlists.single().iconUri)
        val tracks = children(data, CarIds.playlist(9))
        assertEquals(listOf("Şımarık", "Gülümse"), titles(tracks))
        val play = runBlocking { tree(data).play(tracks[1].id) } as CarPlay.Tracks
        assertEquals(1, play.start)
        assertEquals(PlaySource(PlayFrom.Playlist, "Yol şarkıları"), play.source)
    }

    @Test
    fun recentlyPlayedIsTheMusicPlayedLastInItsOrder() {
        val data = FakeCarData(recent = listOf(tarkan[2], gulumse[0]))
        val recent = children(data, CarIds.RECENT)
        assertEquals(listOf("Kuzu Kuzu", "Gülümse"), titles(recent))
        assertEquals("Tarkan · Karma", recent[0].subtitle)
        assertEquals(CarIds.track(CarIds.RECENT, 1), recent[1].id)
    }

    @Test
    fun theEpisodesLeftInTheMiddleComeTheOneListenedToLastFirst() {
        val feeds = listOf(feed(1, "Fularsız Entellik"), feed(2, "Kafa Sesi"))
        val episodes = listOf(
            episode(10, 1, "Sanat ve zanaat", positionMs = 600_000),
            episode(11, 1, "Bitti", positionMs = 3_000_000, played = true),
            episode(12, 2, "Uzay", positionMs = 120_000, downloaded = true),
            // left in its last seconds: not offered to resume
            episode(13, 2, "Sonunda", positionMs = 3_598_000),
            episode(14, 2, "Hiç başlanmadı"),
        )
        val data = FakeCarData(feeds = feeds, episodes = episodes, listened = listOf(12, 10))
        val continuing = children(data, CarIds.CONTINUE)
        assertEquals(listOf(CarIds.episode(12), CarIds.episode(10)), ids(continuing))
        assertEquals(listOf("Kafa Sesi", "Fularsız Entellik"), continuing.map { it.subtitle })
        assertTrue(continuing[0].downloaded)
        assertFalse(continuing[1].downloaded)
        assertEquals(120_000.0 / 3_600_000, continuing[0].progress!!, 1e-9)
        assertEquals("web:https://example.org/cover/2.jpg", continuing[0].iconUri)
        // played or not, an episode plays where the app plays it from (EpisodeMedia)
        val play = runBlocking { tree(data).play(continuing[1].id) } as CarPlay.Episode
        assertEquals(10L, play.episode.id)
        assertEquals("Fularsız Entellik", play.feed.title)
    }

    @Test
    fun aShowOpensOnItsEpisodesTheNewestFirst() {
        val data = FakeCarData(
            feeds = listOf(feed(1, "Fularsız Entellik")),
            episodes = listOf(episode(1, 1, "Birinci"), episode(3, 1, "Üçüncü", played = true), episode(2, 1, "İkinci")),
        )
        val shows = children(data, CarIds.SHOWS)
        assertEquals(listOf(CarIds.show(1)), ids(shows))
        assertEquals(CarStyle.List, shows.single().children)
        val episodes = children(data, CarIds.show(1))
        assertEquals(listOf("Üçüncü", "İkinci", "Birinci"), titles(episodes))
        assertTrue(episodes[0].played)
        assertEquals(1.0, episodes[0].progress!!, 0.0)
        assertEquals("day:3000000", episodes[0].subtitle)
    }

    @Test
    fun theNewEpisodesAreThoseARefreshAddedNotPlayed() {
        val data = FakeCarData(
            feeds = listOf(feed(1, "Fularsız Entellik", subscribedAt = 5_000)),
            episodes = listOf(
                episode(1, 1, "Aboneliktekiler", addedAt = 5_000),
                episode(2, 1, "Yeni", addedAt = 9_000),
                episode(3, 1, "Yeni ama dinlendi", addedAt = 9_000, played = true),
            ),
        )
        assertEquals(listOf(CarIds.episode(2)), ids(children(data, CarIds.NEW)))
    }

    @Test
    fun theRadiosStationsPlayAtOneTap() {
        val eksen = station(1, "Radyo Eksen")
        val data = FakeCarData(favourites = listOf(eksen), recentStations = listOf(station(2, "Açık Radyo"), eksen))
        val favourites = children(data, CarIds.FAVOURITES)
        assertEquals(listOf(CarIds.station(1)), ids(favourites))
        assertTrue(favourites.single().playable)
        assertEquals("Türkiye · pop", favourites.single().subtitle)
        assertEquals("web:https://radio.example.org/logo/1.png", favourites.single().iconUri)
        assertEquals(listOf("Açık Radyo", "Radyo Eksen"), titles(children(data, CarIds.STATIONS_RECENT)))
        assertEquals(CarPlay.Radio(eksen), runBlocking { tree(data).play(CarIds.station(1)) })
    }

    @Test
    fun theCategoriesAreTheDirectorysTagsAndTheirStations() {
        val jazz = station(0, "Jazz FM", uuid = "u-jazz")
        val data = FakeCarData(tags = listOf(DirectoryTag("pop", 900), DirectoryTag("jazz", 300)), tagged = mapOf("jazz" to listOf(jazz)))
        val tags = children(data, CarIds.TAGS)
        assertEquals(listOf(CarIds.tag("pop"), CarIds.tag("jazz")), ids(tags))
        assertEquals(listOf("Pop", "Jazz"), titles(tags))
        assertEquals("300 Stations", tags[1].subtitle)
        val stations = children(data, CarIds.tag("jazz"))
        assertEquals(listOf("Jazz FM"), titles(stations))
        // a station of the directory that is not kept plays from what its id carries
        val play = runBlocking { tree(data).play(stations.single().id) } as CarPlay.Radio
        assertEquals("u-jazz", play.station.uuid)
        assertEquals("Jazz FM", play.station.name)
        assertEquals(jazz.streamUrl, play.station.streamUrl)
        assertEquals(jazz.logoUrl, play.station.logoUrl)
    }

    @Test
    fun anOfflineDirectorySaysSo() {
        val data = FakeCarData(offline = true)
        assertEquals(listOf("RadioOffline"), titles(children(data, CarIds.TAGS)))
        assertEquals(listOf("RadioOffline"), titles(children(data, CarIds.tag("jazz"))))
        assertEquals(listOf(CarIds.nothing("RadioOffline")), ids(children(data, CarIds.TAGS)))
    }

    @Test
    fun aMedialibraryThatDoesNotAnswerLeavesTheRestAnswering() {
        val data = FakeCarData(
            tracks = gulumse,
            feeds = listOf(feed(1, "Tarkan Üzerine")),
            favourites = listOf(station(3, "Tarkan FM")),
            musicUnavailable = true,
        )
        for (id in listOf(CarIds.ALBUMS, CarIds.ARTISTS, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.RECENT, CarIds.album("x"))) {
            assertEquals(id, listOf(CarIds.nothing("MusicUnavailable")), ids(children(data, id)))
        }
        assertNull(runBlocking { tree(data).play(CarIds.track(CarIds.RECENT, 0)) })
        // the podcasts and the radio answer the search, under one line about the music
        val found = runBlocking { tree(data).search("tarkan") }
        assertEquals(listOf("MusicUnavailable", "Tarkan Üzerine", "Tarkan FM"), titles(found))
        assertEquals(listOf(CarIds.show(1)), ids(children(data, CarIds.SHOWS)))
    }

    @Test
    fun aKeyWithAnySignInItComesBack() {
        val folder = "/storage/1A2B-3C4D/Müzik/Rock & Roll?/50% indirim"
        val id = CarIds.track(CarIds.folder(folder), 4)
        val ref = CarIds.parse(id)!!
        assertEquals(CarKind.Folder, ref.kind)
        assertEquals(folder, ref.key)
        assertEquals(4, ref.index)
        assertNull(ref.page)
        val station = CarIds.parse(CarIds.directoryStation("u&1", "A=B?", "https://x.org/s?a=1&b=2", null))!!
        assertEquals("u&1", station.key)
        assertEquals("A=B?", station.params["n"])
        assertEquals("https://x.org/s?a=1&b=2", station.params["u"])
    }

    @Test
    fun theSearchGroupsWhatItFinds() {
        val data = FakeCarData(
            tracks = gulumse + tarkan,
            feeds = listOf(feed(1, "Tarkan Üzerine")),
            episodes = listOf(episode(5, 1, "Tarkan'ın doksanları")),
            favourites = listOf(station(3, "Tarkan FM")),
            directory = listOf(station(0, "Tarkan Radyo", uuid = "u-t")),
        )
        val found = runBlocking { tree(data).search("tarkan") }
        assertEquals(
            listOf("FoundArtists", "FoundEpisodes", "FoundShows", "FoundStations", "FoundStations"),
            found.map { it.group },
        )
        assertEquals(listOf("Tarkan", "Tarkan'ın doksanları", "Tarkan Üzerine", "Tarkan FM", "Tarkan Radyo"), titles(found))

        val tracks = runBlocking { tree(data).search("kuzu") }
        assertEquals(listOf("FoundTracks"), tracks.map { it.group })
        val play = runBlocking { tree(data).play(tracks.single().id) } as CarPlay.Tracks
        assertEquals("Kuzu Kuzu", play.tracks[play.start].title)
    }

    @Test
    fun aSearchThatFindsNothingSaysSo() {
        val data = FakeCarData(tracks = gulumse, offline = true)
        assertEquals(listOf("NoResult"), titles(runBlocking { tree(data).search("metallica") }))
        assertEquals(listOf("NoResult"), titles(runBlocking { tree(data).search("  ") }))
    }
}
