package app.nagmedar.ui.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.MusicView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import java.util.Locale

/**
 * 1.0.7: the palette's media as the app makes them from the medialibrary's items: a track is found by its
 * artist, its album and who the album is filed under; the artists and the albums are found too and open in
 * the Music view; every track found is listed. In 1.0.6 an artist's or an album's name found nothing (a track
 * was looked for in its title alone) and six tracks were shown of however many were found.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class PaletteMediaTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun file(id: Long, title: String, artist: String, album: String, albumArtist: String = artist): MediaWrapper =
        MLServiceLocator.getAbstractMediaWrapper(
            id, "file:///storage/emulated/0/Music/$id.flac", 0L, 0f, 241_000L, MediaWrapper.TYPE_AUDIO, title, "$id.flac",
            7L, 7L, artist, "", 11L, album, albumArtist, 0, 0, null, -2, -2, id.toInt(), 1, 1_760_000_000L, 0L, false, false, 2026, true, 1_760_000_100L,
        )

    private val files = listOf(
        file(1, "Sabaha Karşı", "Ada Karan", "Gece Mavisi"),
        file(2, "Kuzeyden Esen", "Ada Karan", "Gece Mavisi"),
        file(3, "Fener", "Ada Karan", "Liman"),
        // a guest's track on Ada Karan's album
        file(4, "Rıhtım", "Deniz Aral", "Liman", albumArtist = "Ada Karan"),
        file(5, "Şafak", "Deniz Aral", "Kıyı"),
    )

    private val played = mutableListOf<String>()
    private val opened = mutableListOf<GroupItem>()

    /** The tracks alone, as 1.0.6 offered them. */
    private val tracks = mediaItems(res, files) { played += it.title }

    /** As the app offers them: the artists and the albums, then the tracks. */
    private val items = groupItems(res, searchData(files, files, Locale.forLanguageTag("tr")).groups) { opened += it } + tracks

    private fun found(query: String, among: List<PaletteItem> = items) = paletteResults(query, null, among).hits.map { it.item.label }

    @Test
    fun aTrackIsFoundByItsArtistAndItsAlbum() {
        // by the artist, and by who the album is filed under (the guest's track on Ada Karan's album)
        assertEquals(listOf("Fener", "Rıhtım", "Sabaha Karşı", "Kuzeyden Esen"), found("karan", tracks))
        assertEquals(listOf("Şafak", "Rıhtım"), found("deniz aral", tracks))
        // by the album
        assertEquals(listOf("Fener", "Rıhtım"), found("liman", tracks))
        assertEquals(listOf("Sabaha Karşı", "Kuzeyden Esen"), found("gece mavisi", tracks))
        // a word of the title and one of the artist, in either order
        assertEquals(listOf("Kuzeyden Esen"), found("esen karan", tracks))
        assertEquals(listOf("Kuzeyden Esen"), found("karan esen", tracks))
    }

    @Test
    fun aTracksRowNamesItsArtistAndItsAlbum() {
        assertEquals("Parça · Deniz Aral · Liman", tracks.single { it.label == "Rıhtım" }.detail)
        paletteResults("safak", null, tracks).hits.single().item.run()
        assertEquals(listOf("Şafak"), played)
    }

    @Test
    fun theArtistsAndTheAlbumsAreFoundToo() {
        // the artist by the name; then the albums and the tracks found by it
        assertEquals(listOf("Ada Karan", "Liman", "Gece Mavisi", "Fener", "Rıhtım", "Sabaha Karşı", "Kuzeyden Esen"), found("ada karan"))
        assertEquals(listOf("Deniz Aral", "Kıyı", "Şafak", "Rıhtım"), found("deniz aral"))
        assertEquals(listOf("Liman", "Fener", "Rıhtım"), found("liman"))
        assertEquals("Sanatçı · 2 albüm · 4 parça", items.single { it.label == "Ada Karan" }.detail)
        assertEquals("Albüm · Ada Karan · 2 parça", items.single { it.label == "Liman" }.detail)
    }

    @Test
    fun anArtistAndAnAlbumOpenInTheMusicView() {
        paletteResults("liman", null, items).hits.first().item.run()
        paletteResults("deniz aral", null, items).hits.first().item.run()
        assertEquals(listOf(MusicView.Albums to "Liman", MusicView.Artists to "Deniz Aral"), opened.map { it.view to it.title })
        assertTrue(played.isEmpty())
        // what the palette keeps of them (what ran lately) has no control character: the preferences hold it as text
        assertTrue(items.all { item -> item.id.none { it < ' ' } })
    }

    @Test
    fun everyTrackFoundIsListed() {
        // the walk's library: eight albums of eight tracks, two albums an artist
        val walk = (1..8).flatMap { album ->
            (1..8).map { track -> file(100L + album * 10 + track, "Profile track $album.$track", "Nagmedar Artist ${(album + 1) / 2}", "Profile album $album") }
        }
        val media = mediaItems(res, walk) {}
        assertEquals(64, paletteResults("profile", null, media).hits.size)
        assertEquals(64, paletteResults("@profile", null, media).hits.size)
        // an artist's sixteen, by the name as a phrase (a "3" alone is in other titles too)
        assertEquals(16, paletteResults("\"nagmedar artist 3\"", null, media).hits.size)
        assertEquals(8, paletteResults("\"profile album 5\"", null, media).hits.size)
    }
}
