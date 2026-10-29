package app.nagmedar.ui.auto

import app.nagmedar.ui.music.MusicGroup
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import app.nagmedar.ui.playback.PlayFrom
import app.nagmedar.ui.playback.PlaySource
import org.videolan.vlc.media.VoiceFocus
import org.videolan.vlc.media.VoiceRequest

/**
 * 1.0: what a spoken request ("Hey Google, … çal") plays. Music first, by the field the assistant names (an artist, an
 * album, a title, a genre, a playlist) or, with none, by whichever answers best; then podcasts (a show by its title, which
 * plays where it was left, or an episode by its own); then radio (the favourite and recent stations, then the directory).
 * A request that names its kind ("… podcast'ini çal", "… radyosunu aç") looks there alone; one that names only its kind
 * plays that kind's latest ("radyo aç": the station played last). Nothing that answers less than word for word plays.
 */
class CarVoiceSearch(private val data: CarData) {

    suspend fun resolve(request: VoiceRequest): CarPlay? {
        val query = request.parsed
        val kind = query.kind
        if (kind == VoiceKind.Any || kind == VoiceKind.Music) music(request)?.let { return it }
        // the assistant may take a show's or a station's name for an artist: the words heard are looked for there too
        val words = query.text
        if (kind == VoiceKind.Any || kind == VoiceKind.Podcast) podcast(words)?.let { return it }
        if (kind == VoiceKind.Any || kind == VoiceKind.Radio) radio(words)?.let { return it }
        return null
    }

    private suspend fun music(request: VoiceRequest): CarPlay? {
        val library = data.music()
        if (library.tracks.isEmpty() && request.focus != VoiceFocus.Playlist) return null
        val words = request.parsed.text
        return when (request.focus) {
            VoiceFocus.Artist -> artist(library, request.artist ?: words)
            VoiceFocus.Album -> album(library, request.album ?: words, request.artist)
            VoiceFocus.Title -> title(library, request.title ?: words, request.artist)
            VoiceFocus.Genre -> genre(library, request.genre ?: words)
            VoiceFocus.Playlist -> playlist(request.playlist ?: words)
            null -> when {
                words.isNotEmpty() -> unstructured(library, words)
                // "müzik çal": the music played last, else all of it
                request.parsed.kind == VoiceKind.Music -> data.recentTracks().takeIf { it.isNotEmpty() }?.let { CarPlay.Tracks(it, 0) }
                    ?: library.groups(MusicView.Tracks).firstOrNull()?.tracks?.takeIf { it.isNotEmpty() }?.let { CarPlay.Tracks(it, 0) }
                else -> null
            }
        }
    }

    /** No field named: the artist, album, playlist, title or genre that answers best, in that order between equals. */
    private suspend fun unstructured(library: MusicLibrary, words: String): CarPlay? {
        val artist = CarVoice.best(words, library.groups(MusicView.Artists)) { it.title }
        val album = CarVoice.best(words, library.groups(MusicView.Albums)) { it.title }
        val playlist = CarVoice.best(words, data.playlists()) { it.name }
        val title = CarVoice.best(words, library.tracks) { it.title }
        val genre = CarVoice.best(words, library.groups(MusicView.Genres)) { it.title }
        val scores = listOf(artist?.second, album?.second, playlist?.second, title?.second, genre?.second)
        val best = scores.maxOf { it ?: 0 }
        if (best == 0) return null
        return when (scores.indexOfFirst { it == best }) {
            0 -> artistPlay(artist!!.first)
            1 -> albumPlay(album!!.first)
            2 -> playlist!!.first.let { list -> data.playlistTracks(list.id).takeIf { it.isNotEmpty() }?.let { CarPlay.Tracks(it, 0, PlaySource(PlayFrom.Playlist, list.name)) } }
            3 -> titlePlay(library, title!!.first)
            else -> CarPlay.Tracks(genre!!.first.tracks, 0)
        }
    }

    private fun artist(library: MusicLibrary, name: String): CarPlay? =
        CarVoice.best(name, library.groups(MusicView.Artists)) { it.title }?.first?.let(::artistPlay)

    private fun album(library: MusicLibrary, name: String, artist: String?): CarPlay? {
        val albums = library.groups(MusicView.Albums)
        val byArtist = artist?.let { who -> albums.filter { CarVoice.score(who, it.artist) >= CarVoice.ENOUGH } }.orEmpty()
        val found = CarVoice.best(name, byArtist) { it.title } ?: CarVoice.best(name, albums) { it.title }
        return found?.first?.let(::albumPlay)
    }

    private fun title(library: MusicLibrary, name: String, artist: String?): CarPlay? {
        val byArtist = artist?.let { who -> library.tracks.filter { CarVoice.score(who, it.artist ?: it.filedArtist) >= CarVoice.ENOUGH } }.orEmpty()
        val found = CarVoice.best(name, byArtist) { it.title } ?: CarVoice.best(name, library.tracks) { it.title }
        return found?.first?.let { titlePlay(library, it) }
    }

    private fun genre(library: MusicLibrary, name: String): CarPlay? =
        CarVoice.best(name, library.groups(MusicView.Genres)) { it.title }?.first?.let { CarPlay.Tracks(it.tracks, 0) }

    private suspend fun playlist(name: String): CarPlay? {
        val list = CarVoice.best(name, data.playlists()) { it.name }?.first ?: return null
        return data.playlistTracks(list.id).takeIf { it.isNotEmpty() }?.let { CarPlay.Tracks(it, 0, PlaySource(PlayFrom.Playlist, list.name)) }
    }

    private fun artistPlay(group: MusicGroup) = CarPlay.Tracks(group.tracks, 0, group.title?.let { PlaySource(PlayFrom.Artist, it) })

    private fun albumPlay(group: MusicGroup) = CarPlay.Tracks(group.tracks, 0, group.title?.let { PlaySource(PlayFrom.Album, it) })

    /** A track asked for by its title plays with its album after it. */
    private fun titlePlay(library: MusicLibrary, track: Track): CarPlay {
        val album = library.groups(MusicView.Albums).firstOrNull { it.key == MusicLibrary.albumKey(track.filedArtist, track.album) }
        val index = album?.tracks?.indexOfFirst { it.uri == track.uri } ?: -1
        return if (album != null && index >= 0) CarPlay.Tracks(album.tracks, index, album.title?.let { PlaySource(PlayFrom.Album, it) })
        else CarPlay.Tracks(listOf(track), 0)
    }

    /** A show by its title (where it was left, else its newest), or an episode by its own; nothing named: the one left in the middle last. */
    private suspend fun podcast(words: String): CarPlay? {
        val feeds = data.feeds()
        if (feeds.isEmpty()) return null
        val continuing = data.continuing()
        if (words.isEmpty()) {
            val episode = continuing.firstOrNull() ?: data.fresh().firstOrNull() ?: return null
            return feeds.firstOrNull { it.id == episode.feedId }?.let { CarPlay.Episode(it, episode) }
        }
        val show = CarVoice.best(words, feeds) { it.title }
        val episode = CarVoice.best(words, data.findEpisodes(words, MAX_EPISODES)) { it.title }
        if (show != null && show.second >= (episode?.second ?: 0)) {
            val feed = show.first
            val chosen = CarPodcasts.pick(data.episodes(feed.id), continuing) ?: return null
            return CarPlay.Episode(feed, chosen)
        }
        val found = episode?.first ?: return null
        return feeds.firstOrNull { it.id == found.feedId }?.let { CarPlay.Episode(it, found) }
    }

    /** A kept station by its name, else the directory's; nothing named: the station played last, else the first favourite. */
    private suspend fun radio(words: String): CarPlay? {
        val recent = data.recentStations()
        val favourites = data.favouriteStations()
        if (words.isEmpty()) return (recent.firstOrNull() ?: favourites.firstOrNull())?.let(CarPlay::Radio)
        CarVoice.best(words, (favourites + recent).distinctBy { it.key }) { it.name }?.let { return CarPlay.Radio(it.first) }
        val found = CarTree.quietly { data.findStations(words) }.orEmpty()
        val station = CarVoice.best(words, found, least = CarVoice.PART) { it.name }?.first ?: found.firstOrNull() ?: return null
        return CarPlay.Radio(station)
    }

    private companion object {
        const val MAX_EPISODES = 50
    }
}
