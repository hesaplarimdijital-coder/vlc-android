package app.nagmedar.ui.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.MusicLibrary
import app.nagmedar.ui.music.MusicView
import app.nagmedar.ui.music.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * 1.0.7: the search's artists and albums are the music library's groups: the same keys (the Music view opens
 * them), who an album is filed under, how much each holds; tracks without the tag make no group; the key the
 * palette keeps what ran lately by has no control character. (Run with Android's classes: whether a track is on
 * a network share is read from its address by android.net.Uri.)
 */
@RunWith(AndroidJUnit4::class)
class SearchGroupsTest {

    private var next = 1L

    private fun track(title: String, artist: String?, album: String?, albumArtist: String? = null, uri: String? = null): Track {
        val id = next++
        return Track(
            id = id, uri = uri ?: "file:///storage/emulated/0/Music/$id.flac", title = title, artist = artist, albumArtist = albumArtist, album = album,
            genre = null, year = null, trackNumber = id.toInt(), discNumber = 1, durationMs = 200_000, folder = "/storage/emulated/0/Music",
        )
    }

    private val library = MusicLibrary(
        listOf(
            track("Sabaha Karşı", "Ada Karan", "Gece Mavisi"),
            track("Kuzeyden Esen", "Ada Karan", "Gece Mavisi"),
            track("Fener", "Ada Karan", "Liman"),
            // a guest's track on the album: filed under the album's artist
            track("Rıhtım", "Deniz Aral", "Liman", albumArtist = "Ada Karan"),
            track("Şafak", "Deniz Aral", "Kıyı", uri = "smb://nas/Muzik/Deniz Aral/Safak.flac"),
            track("Etiketsiz", null, null),
        ),
        Locale.forLanguageTag("tr"),
    )

    private val groups = searchGroups(library)

    @Test
    fun theArtistsThenTheAlbumsAsTheMusicViewGroupsThem() {
        assertEquals(
            listOf("Ada Karan" to MusicView.Artists, "Deniz Aral" to MusicView.Artists, "Gece Mavisi" to MusicView.Albums, "Liman" to MusicView.Albums, "Kıyı" to MusicView.Albums),
            groups.map { it.title to it.view },
        )
        // each opens by the library's own key
        assertEquals(library.groups(MusicView.Artists).filter { it.title != null }.map { it.key }, groups.filter { it.view == MusicView.Artists }.map { it.group })
        assertEquals(library.groups(MusicView.Albums).filter { it.title != null }.map { it.key }, groups.filter { it.view == MusicView.Albums }.map { it.group })
    }

    @Test
    fun whoAnAlbumIsFiledUnderAndHowMuchEachHolds() {
        val ada = groups.single { it.title == "Ada Karan" }
        assertEquals("the guest's track is on Ada Karan's album", 4, ada.tracks)
        assertEquals(2, ada.albums)
        assertEquals("", ada.artist)
        val liman = groups.single { it.title == "Liman" }
        assertEquals("Ada Karan", liman.artist)
        assertEquals(2, liman.tracks)
        assertEquals(0, liman.albums)
        // Deniz Aral's own: one track, one album
        assertEquals(1 to 1, groups.single { it.title == "Deniz Aral" }.let { it.tracks to it.albums })
    }

    @Test
    fun aGroupWithNothingOnTheDeviceIsOnTheNetwork() {
        assertEquals(listOf("Deniz Aral", "Kıyı"), groups.filter { it.onNetwork }.map { it.title })
    }

    @Test
    fun theKeysAreApartAndHaveNoControlCharacter() {
        assertEquals(groups.size, groups.map { it.key }.toSet().size)
        assertTrue(groups.joinToString { it.key }, groups.all { group -> group.key.none { it < ' ' } })
        assertTrue(groups.filter { it.view == MusicView.Artists }.all { it.key.startsWith("artist:") })
        assertTrue(groups.filter { it.view == MusicView.Albums }.all { it.key.startsWith("album:") })
        // the library's own key of an album has one (it parts the artist and the name): kept for opening it
        assertTrue(groups.filter { it.view == MusicView.Albums }.all { group -> group.group.any { it < ' ' } })
    }
}
