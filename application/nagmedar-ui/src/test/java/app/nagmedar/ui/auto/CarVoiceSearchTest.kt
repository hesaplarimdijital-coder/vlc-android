package app.nagmedar.ui.auto

import app.nagmedar.ui.music.PlaylistSummary
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.videolan.vlc.media.VoiceFocus
import org.videolan.vlc.media.VoiceRequest

/** 1.0: "Hey Google, … çal": what each kind of request plays, and where it looks when the first place has nothing. */
class CarVoiceSearchTest {

    private val gulumse = listOf(track(1, "Gülümse", number = 1), track(2, "Hadi Bakalım", number = 2), track(3, "Ben Şimdi", number = 3))
    private val tarkan = listOf(
        track(4, "Şımarık", artist = "Tarkan", album = "Ölürüm Sana", genre = "Dance", number = 1),
        track(5, "Kuzu Kuzu", artist = "Tarkan", album = "Karma", genre = "Dance", number = 1),
        track(6, "Gülümse Kaderine", artist = "Tarkan", album = "Karma", genre = "Dance", number = 2),
    )
    private val roadTrip = PlaylistSummary(9, "Yol Listesi", 2, 400_000)
    private val feeds = listOf(feed(1, "Fularsız Entellik"), feed(2, "Kafa Sesi"))
    private val episodes = listOf(
        episode(10, 1, "Sanat ve zanaat", positionMs = 600_000),
        episode(11, 1, "Yeni bölüm", addedAt = 9_000),
        episode(12, 2, "Uzay yolculuğu"),
    )
    private val eksen = station(1, "Radyo Eksen")
    private val acik = station(2, "Açık Radyo")

    private fun data() = FakeCarData(
        tracks = gulumse + tarkan,
        playlists = mapOf(roadTrip to listOf(tarkan[1], gulumse[0])),
        feeds = feeds,
        episodes = episodes,
        listened = listOf(10),
        favourites = listOf(eksen),
        recentStations = listOf(acik),
        directory = listOf(station(0, "Jazz FM", uuid = "u-jazz")),
    )

    private fun resolve(request: VoiceRequest, data: FakeCarData = data()) = runBlocking { CarVoiceSearch(data).resolve(request) }

    private fun tracksOf(play: CarPlay?) = (play as CarPlay.Tracks).let { it.tracks.map { track -> track.title } to it.start }

    @Test
    fun anArtistAsTheAssistantNamesIt() {
        val play = resolve(VoiceRequest("tarkan", focus = VoiceFocus.Artist, artist = "Tarkan")) as CarPlay.Tracks
        assertEquals(setOf("Şımarık", "Kuzu Kuzu", "Gülümse Kaderine"), play.tracks.map { it.title }.toSet())
        assertEquals(PlaySource(PlayFrom.Artist, "Tarkan"), play.source)
    }

    @Test
    fun anAlbumOfTheArtistNamed() {
        val play = resolve(VoiceRequest("karma", focus = VoiceFocus.Album, album = "Karma", artist = "Tarkan")) as CarPlay.Tracks
        assertEquals(listOf("Kuzu Kuzu", "Gülümse Kaderine"), play.tracks.map { it.title })
        assertEquals(PlaySource(PlayFrom.Album, "Karma"), play.source)
    }

    @Test
    fun aTitlePlaysWithItsAlbumAfterIt() {
        // two titles start alike: the artist named tells them apart
        assertEquals(listOf("Kuzu Kuzu", "Gülümse Kaderine") to 1, tracksOf(resolve(VoiceRequest("gülümse", focus = VoiceFocus.Title, title = "Gülümse", artist = "Tarkan"))))
        assertEquals(listOf("Gülümse", "Hadi Bakalım", "Ben Şimdi") to 0, tracksOf(resolve(VoiceRequest("gülümse", focus = VoiceFocus.Title, title = "Gülümse (Live)".substringBefore('(').trim()))))
    }

    @Test
    fun aGenreAndAPlaylist() {
        assertEquals(3, (resolve(VoiceRequest("dance", focus = VoiceFocus.Genre, genre = "Dance")) as CarPlay.Tracks).tracks.size)
        val list = resolve(VoiceRequest("yol listesi", focus = VoiceFocus.Playlist, playlist = "Yol Listesi")) as CarPlay.Tracks
        assertEquals(listOf("Kuzu Kuzu", "Gülümse"), list.tracks.map { it.title })
        assertEquals(PlaySource(PlayFrom.Playlist, "Yol Listesi"), list.source)
    }

    @Test
    fun aPlainRequestTakesWhatAnswersBest() {
        assertEquals(PlaySource(PlayFrom.Artist, "Tarkan"), (resolve(VoiceRequest("Tarkan'ı çal")) as CarPlay.Tracks).source)
        assertEquals(PlaySource(PlayFrom.Album, "Karma"), (resolve(VoiceRequest("Karma albümünü çal")) as CarPlay.Tracks).source)
        assertEquals(listOf("Kuzu Kuzu", "Gülümse Kaderine") to 0, tracksOf(resolve(VoiceRequest("kuzu kuzu oynat"))))
    }

    @Test
    fun musicFirstThenPodcastsThenRadio() {
        // no music by that name: the show plays where it was left
        val show = resolve(VoiceRequest("Fularsız Entellik'i aç")) as CarPlay.Episode
        assertEquals(10L, show.episode.id)
        // an episode by its own title
        assertEquals(12L, (resolve(VoiceRequest("uzay yolculuğu")) as CarPlay.Episode).episode.id)
        // a favourite station, then the directory's
        assertEquals(eksen, (resolve(VoiceRequest("Radyo Eksen'i aç")) as CarPlay.Radio).station)
        assertEquals("u-jazz", (resolve(VoiceRequest("jazz fm")) as CarPlay.Radio).station.uuid)
    }

    @Test
    fun theAssistantsWrongFocusStillFindsTheShow() {
        val play = resolve(VoiceRequest("kafa sesi", focus = VoiceFocus.Artist, artist = "Kafa Sesi")) as CarPlay.Episode
        assertEquals(2L, play.feed.id)
        assertEquals(12L, play.episode.id)
    }

    @Test
    fun aRequestThatNamesItsKindLooksThereAlone() {
        assertEquals(1L, (resolve(VoiceRequest("Fularsız Entellik podcast'ini çal")) as CarPlay.Episode).feed.id)
        assertEquals(eksen, (resolve(VoiceRequest("Eksen radyosunu aç")) as CarPlay.Radio).station)
        // a radio by a music name finds no music
        assertNull(resolve(VoiceRequest("Tarkan radyosunu aç"), data().apply { directory = emptyList() }))
    }

    @Test
    fun aKindAlonePlaysItsLatest() {
        // "radyo aç": the station played last
        assertEquals(acik, (resolve(VoiceRequest("radyo aç")) as CarPlay.Radio).station)
        // "podcast dinle": the episode left in the middle last
        assertEquals(10L, (resolve(VoiceRequest("podcast dinle")) as CarPlay.Episode).episode.id)
        assertEquals(listOf("Gülümse") to 0, tracksOf(resolve(VoiceRequest("müzik çal"), data().apply { recent = listOf(gulumse[0]) })))
    }

    @Test
    fun nothingThatAnswersPlaysNothing() {
        assertNull(resolve(VoiceRequest("metallica"), data().apply { directory = emptyList() }))
        // the directory not answering is no failure: nothing found
        assertNull(resolve(VoiceRequest("metallica"), data().apply { offline = true }))
    }

    @Test
    fun anEmptyLibraryLooksElsewhere() {
        val play = resolve(VoiceRequest("radyo eksen"), FakeCarData(favourites = listOf(eksen)))
        assertEquals(CarPlay.Radio(eksen), play)
    }
}
