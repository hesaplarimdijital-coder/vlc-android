package app.nagmedar.ui.auto

import app.nagmedar.ui.music.MusicColumn
import app.nagmedar.ui.music.MusicSort
import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.music.SmartField
import app.nagmedar.ui.music.SmartList
import app.nagmedar.ui.music.SmartOp
import app.nagmedar.ui.music.SmartRule
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import app.nagmedar.ui.stats.TrackStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.media.CarItem
import org.videolan.vlc.media.CarStyle
import org.videolan.vlc.media.MediaSessionBrowser
import java.util.Locale

/**
 * 1.0.3: Android Auto's Music tab as the user asked for it ("müziklerimi seçebilmeliyim"): the whole music shuffled at
 * one tap, the genres when the music has some, the smart lists after the playlists, every track A–Z in pages named by
 * their letters; each list plays from the track chosen and queues the rest.
 */
class CarMusicTest {

    private val pop = listOf(track(1, "Gülümse"), track(2, "Hadi Bakalım"), track(3, "Ben Şimdi"))
    private val rock = listOf(
        track(4, "Islak Islak", artist = "Barış Manço", album = "Sakla Samanı", genre = "Rock", number = 1),
        track(5, "Arkadaşım Eşek", artist = "Barış Manço", album = "Sakla Samanı", genre = " rock ", number = 2),
    )
    private val untagged = track(6, "Çiftetelli", artist = "Anonim", album = null, genre = null)

    private fun tree(data: FakeCarData, page: Int = CarTree.PAGE) = CarTree(data, FakeCarLook, page)

    private fun children(data: FakeCarData, id: String, page: Int = CarTree.PAGE): List<CarItem> =
        runBlocking { tree(data, page).children(id) } ?: error("no children of $id")

    private fun play(data: FakeCarData, id: String, page: Int = CarTree.PAGE): CarPlay? = runBlocking { tree(data, page).play(id) }

    private fun ids(items: List<CarItem>) = items.map { it.id }

    private fun titles(items: List<CarItem>) = items.map { it.title }

    @Test
    fun theMusicTabShufflesFirstThenItsListsWithGenresWhenTheMusicHasSome() {
        val data = FakeCarData(tracks = pop + rock + untagged)
        val music = children(data, CarIds.MUSIC)
        assertEquals(
            listOf(CarIds.SHUFFLE, CarIds.ALBUMS, CarIds.ARTISTS, CarIds.GENRES, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.TRACKS),
            ids(music),
        )
        assertEquals(listOf("ShuffleAll", "Albums", "Artists", "Genres", "Playlists", "Years", "Folders", "AllTracks"), titles(music))
        // the shuffle plays at one tap; every other entry opens a list
        val shuffle = music.first()
        assertTrue(shuffle.playable && !shuffle.browsable)
        assertEquals("icon:Shuffle", shuffle.iconUri)
        assertTrue(music.drop(1).all { it.browsable && !it.playable })
        assertEquals(listOf("icon:Genres", "icon:Tracks"), listOf(music[3].iconUri, music[7].iconUri))
        assertEquals(CarStyle.List, music[3].children)
        assertEquals(CarStyle.List, music[7].children)
        // a music without a genre, or none at all, has no Genres
        assertFalse(CarIds.GENRES in ids(children(FakeCarData(tracks = listOf(untagged)), CarIds.MUSIC)))
        // music the medialibrary does not give in time leaves Genres out, not the tab
        val late = children(FakeCarData(tracks = pop, musicUnavailable = true), CarIds.MUSIC)
        assertEquals(listOf(CarIds.SHUFFLE, CarIds.ALBUMS, CarIds.ARTISTS, CarIds.PLAYLISTS, CarIds.YEARS, CarIds.FOLDERS, CarIds.TRACKS), ids(late))
    }

    @Test
    fun shuffleAllPlaysTheWholeMusicFromItsFirstTrack() {
        val data = FakeCarData(tracks = pop + rock + untagged)
        val shuffled = play(data, CarIds.SHUFFLE) as CarPlay.Tracks
        assertEquals(0, shuffled.start)
        assertEquals((pop + rock + untagged).map { it.id }.toSet(), shuffled.tracks.map { it.id }.toSet())
        assertEquals(6, shuffled.tracks.size)
        assertNull(shuffled.source)
        // nothing to shuffle, nothing plays (Auto says why); nor when the medialibrary does not answer
        assertNull(play(FakeCarData(), CarIds.SHUFFLE))
        assertNull(play(FakeCarData(tracks = pop, musicUnavailable = true), CarIds.SHUFFLE))
    }

    @Test
    fun theGenresAreTheMusicPagesWithTheTracksOfNoneLast() {
        val data = FakeCarData(tracks = pop + rock + untagged)
        val genres = children(data, CarIds.GENRES)
        // "Rock" and " rock " are one genre, by its most common spelling; the tracks of no genre last
        assertEquals(listOf("Pop", "Rock", "UnknownGenre"), titles(genres))
        assertEquals(listOf(CarIds.genre("pop"), CarIds.genre("rock"), CarIds.genre("")), ids(genres))
        assertEquals(listOf("3 Tracks", "2 Tracks", "1 Tracks"), genres.map { it.subtitle })
        assertEquals("cover:file:///music/4.flac", genres[1].iconUri)
        assertTrue(genres.all { it.browsable && it.children == CarStyle.List })

        val rockTracks = children(data, genres[1].id)
        assertEquals(listOf("Islak Islak", "Arkadaşım Eşek"), titles(rockTracks))
        assertEquals("Barış Manço · Sakla Samanı", rockTracks[0].subtitle)
        // the second plays the genre from there
        val played = play(data, rockTracks[1].id) as CarPlay.Tracks
        assertEquals(listOf(4L, 5L), played.tracks.map { it.id })
        assertEquals(1, played.start)

        // the group of no genre opens and plays too: its key is empty
        val none = children(data, CarIds.genre(""))
        assertEquals(listOf("Çiftetelli"), titles(none))
        assertEquals(listOf(6L), (play(data, none.single().id) as CarPlay.Tracks).tracks.map { it.id })
        // a genre that is no longer there says so
        assertEquals(listOf(CarIds.nothing("Empty")), ids(children(data, CarIds.genre("caz"))))
        assertNull(play(data, CarIds.track(CarIds.genre("caz"), 0)))
    }

    @Test
    fun theSmartListsFollowThePlaylistsAndOpenOnWhatTheyTakeNow() {
        val loved = SmartList(id = 7, name = "Sevdiklerim", rules = listOf(SmartRule(SmartField.Rating, SmartOp.AtLeast, "4")), sort = MusicSort(MusicColumn.Rating, false))
        val never = SmartList(id = 8, name = "Hiç çalınmayanlar", rules = listOf(SmartRule(SmartField.LastPlayed, SmartOp.Never)))
        val road = PlaylistSummary(9, "Yol", 1, 200_000)
        val data = FakeCarData(
            tracks = pop + rock,
            playlists = mapOf(road to listOf(rock[0])),
            smart = listOf(loved, never),
            stats = mapOf(
                pop[0].uri to TrackStats(rating = 4, plays = 3, lastPlayedAt = 99L * DAY_MS),
                rock[1].uri to TrackStats(rating = 5, plays = 1, lastPlayedAt = 98L * DAY_MS),
                pop[2].uri to TrackStats(rating = 2, plays = 7, lastPlayedAt = 97L * DAY_MS),
            ),
        )
        val lists = children(data, CarIds.PLAYLISTS)
        assertEquals(listOf(CarIds.playlist(9), CarIds.smart(7), CarIds.smart(8)), ids(lists))
        assertEquals(listOf("Yol", "Sevdiklerim", "Hiç çalınmayanlar"), titles(lists))
        // a smart list says what it is, not a count it would have to read the music for
        assertEquals(listOf("1 Tracks", "Smart", "Smart"), lists.map { it.subtitle })
        assertEquals("icon:Smart", lists[1].iconUri)
        assertTrue(lists.all { it.browsable && it.children == CarStyle.List })

        // the ratings' list, the best first, as its sort asks
        val lovedTracks = children(data, CarIds.smart(7))
        assertEquals(listOf("Arkadaşım Eşek", "Gülümse"), titles(lovedTracks))
        val played = play(data, lovedTracks[1].id) as CarPlay.Tracks
        assertEquals(listOf(5L, 1L), played.tracks.map { it.id })
        assertEquals(1, played.start)
        assertEquals(PlaySource(PlayFrom.Playlist, "Sevdiklerim"), played.source)
        // the tracks never played, in the library's order
        assertEquals(setOf("Hadi Bakalım", "Islak Islak"), titles(children(data, CarIds.smart(8))).toSet())
    }

    @Test
    fun aSmartListThatTakesNothingOrIsGoneSaysSo() {
        val loved = SmartList(id = 7, name = "Sevdiklerim", rules = listOf(SmartRule(SmartField.Rating, SmartOp.AtLeast, "4")))
        val data = FakeCarData(tracks = pop, smart = listOf(loved))
        assertEquals(listOf(CarIds.nothing("Empty")), ids(children(data, CarIds.smart(7))))
        assertNull(play(data, CarIds.track(CarIds.smart(7), 0)))
        // deleted since Auto listed it
        assertEquals(listOf(CarIds.nothing("Empty")), ids(children(data, CarIds.smart(70))))
        assertNull(play(data, CarIds.track(CarIds.smart(70), 0)))
        // the smart lists alone fill the list of playlists
        assertEquals(listOf(CarIds.smart(7)), ids(children(data, CarIds.PLAYLISTS)))
        // the music not given in time: one line, as for every list of music
        data.musicUnavailable = true
        assertEquals(listOf(CarIds.nothing("MusicUnavailable")), ids(children(data, CarIds.smart(7))))
        assertNull(play(data, CarIds.track(CarIds.smart(7), 0)))
    }

    @Test
    fun everyTrackIsListedAToZAndPlaysTheWholeListFromTheOneChosen() {
        val data = FakeCarData(tracks = pop + rock + untagged)
        val all = children(data, CarIds.TRACKS)
        // by title, as people read them (the tests' library reads in Locale.ROOT)
        assertEquals(listOf("Arkadaşım Eşek", "Ben Şimdi", "Çiftetelli", "Gülümse", "Hadi Bakalım", "Islak Islak"), titles(all))
        assertTrue(all.all { it.playable && !it.browsable })
        assertEquals(CarIds.track(CarIds.TRACKS, 3), all[3].id)
        val played = play(data, all[3].id) as CarPlay.Tracks
        assertEquals(6, played.tracks.size)
        assertEquals(3, played.start)
        assertEquals("Gülümse", played.tracks[played.start].title)
        assertNull(played.source)
    }

    @Test
    fun aLongListOfEveryTrackIsCutIntoPagesNamedByTheirFirstLetters() {
        val titles = listOf("Ağla", "Ayrılık", "Bir Ses", "Çay", "Dön", "İstanbul", "Islak", "Zaman", "3 Kere")
        val data = FakeCarData(tracks = titles.mapIndexed { i, title -> track(i + 1L, title, album = "Albüm ${i + 1}") })
        val pages = children(data, CarIds.TRACKS, page = 3)
        // "3 Kere" comes first; within a page its first and last letters, one letter once
        assertEquals(listOf(CarIds.page(CarIds.TRACKS, 0), CarIds.page(CarIds.TRACKS, 1), CarIds.page(CarIds.TRACKS, 2)), ids(pages))
        assertEquals(listOf("# – A", "B – D", "I – Z"), titles(pages))
        assertTrue(pages.all { it.browsable && it.children == CarStyle.List })
        val second = children(data, pages[1].id, page = 3)
        assertEquals(listOf("Bir Ses", "Çay", "Dön"), titles(second))
        // a track of the second page plays every track from it
        val played = play(data, second[1].id, page = 3) as CarPlay.Tracks
        assertEquals(9, played.tracks.size)
        assertEquals(4, played.start)
        assertEquals("Çay", played.tracks[played.start].title)
    }

    @Test
    fun aPageNamedByItsLettersWritesThemAsTheLanguageDoes() {
        val turkish = Locale.forLanguageTag("tr-TR")
        assertEquals("İ", CarTree.initial("ilk", turkish))
        assertEquals("I", CarTree.initial("ılık", turkish))
        assertEquals("Ç", CarTree.initial("  çay", turkish))
        assertEquals("I", CarTree.initial("ilk", Locale.ENGLISH))
        assertEquals("#", CarTree.initial("1990", turkish))
        assertEquals("#", CarTree.initial("", turkish))
    }

    @Test
    fun aGenresOrFoldersEmptyKeyIsReadBackAsOne() {
        // the group of the tracks of no genre (or of no artist) has an empty key: its id is still the tree's
        val genre = CarIds.parse(CarIds.track(CarIds.genre(""), 2))!!
        assertEquals(CarKind.Genre, genre.kind)
        assertEquals("", genre.key)
        assertEquals(2, genre.index)
        val smart = CarIds.parse(CarIds.page(CarIds.smart(12), 1))!!
        assertEquals(CarKind.Smart, smart.kind)
        assertEquals(12L, smart.number)
        assertEquals(1, smart.page)
        // the lists' own paths have no key
        for (id in listOf(CarIds.GENRES, CarIds.TRACKS, CarIds.SHUFFLE, CarIds.PLAYLISTS, CarIds.FOLDERS)) {
            val ref = CarIds.parse(id)!!
            assertNull(id, ref.kind)
            assertNull(id, ref.key)
        }
        // a tag or a directory station without a key is nothing to ask the directory for
        val data = FakeCarData(tags = emptyList())
        assertEquals(emptyList<CarItem>(), children(data, "${CarIds.RADIO}/tag/"))
        assertEquals(0, data.directoryAsked)
        assertTrue(MediaSessionBrowser.isNoMedia(CarIds.nothing("Empty")))
    }

    @Test
    fun theMusicSearchStillFindsTheTracksOfTheNewLists() {
        // the shuffle, the genres and every track add no heading of their own to the search
        val data = FakeCarData(tracks = pop + rock)
        val found = runBlocking { tree(data).search("ıslak") }
        assertEquals(listOf("FoundTracks"), found.map { it.group })
        assertEquals(listOf("Islak Islak"), titles(found))
    }
}
