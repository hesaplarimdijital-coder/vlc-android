package app.nagmedar.ui.covers

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** S-1006: what MusicBrainz and Cover Art Archive are asked, MusicBrainz's answers read, a folder's cover chosen. */
@RunWith(AndroidJUnit4::class)
class CoverArtTest {

    @Test
    fun theAddresses() {
        assertEquals(
            "https://coverartarchive.org/release-group/6c2e2f0e-3a3d-4b8e-9f0e-8d7b2a1c0e11/front-1200",
            CoverArt.frontUrl(CoverArtArchive.ARCHIVE_URL, "release-group", "6c2e2f0e-3a3d-4b8e-9f0e-8d7b2a1c0e11").toString(),
        )
        val search = CoverArt.searchUrl(CoverArtArchive.MUSICBRAINZ_URL, "Kadri \"KE\" Erdem", "Itrî'nin İzinde")
        assertEquals("musicbrainz.org", search.host)
        assertEquals("/ws/2/release-group/", search.encodedPath)
        assertEquals("releasegroup:\"Itrî'nin İzinde\" AND artist:\"Kadri \\\"KE\\\" Erdem\"", search.queryParameter("query"))
        assertEquals("json", search.queryParameter("fmt"))
        assertEquals("Nagmedar/3.6.0 ( Android music player )", CoverArtArchive.userAgent("3.6.0"))
    }

    @Test
    fun theReleaseGroupFound() {
        val answer = """{"created":"2026-10-12T20:00:00.000Z","count":3,"offset":0,"release-groups":[
            {"id":"11111111-1111-1111-1111-111111111111","score":100,"title":"Itri'nin Izinde (Live)","primary-type":"Album"},
            {"id":"22222222-2222-2222-2222-222222222222","score":97,"title":"ITRÎ'NİN İZİNDE","primary-type":"Album"},
            {"id":"33333333-3333-3333-3333-333333333333","score":60,"title":"Itrî'nin İzinde","primary-type":"Album"}
        ]}"""
        // the title compared without case or accents; another title, or a low score, is not it
        assertEquals("22222222-2222-2222-2222-222222222222", CoverArt.releaseGroup(answer, "Itrî'nin İzinde"))
        assertNull(CoverArt.releaseGroup(answer, "Başka Bir Albüm"))
        assertNull(CoverArt.releaseGroup("""{"release-groups":[]}""", "Itrî'nin İzinde"))
        try {
            CoverArt.releaseGroup("<html>502</html>", "x")
            fail()
        } catch (e: IOException) {
            // garbled: the server's failure, not "no cover"
        }
    }

    @Test
    fun theFolderCover() {
        assertEquals("Folder.JPG", DeviceCoverSources.folderCover(listOf("01.flac", "back.jpg", "Folder.JPG")))
        assertEquals("cover.png", DeviceCoverSources.folderCover(listOf("01.flac", "back.jpg", "Folder.JPG", "cover.png")))
        assertEquals("AlbumArt_{1234}_Large.jpg", DeviceCoverSources.folderCover(listOf("AlbumArtSmall.jpg", "AlbumArt_{1234}_Large.jpg", "AlbumArt_{1234}_Small.jpg")))
        assertEquals("scan.webp", DeviceCoverSources.folderCover(listOf("01.flac", "scan.webp", "notes.txt")))
        // two images, neither named: neither
        assertNull(DeviceCoverSources.folderCover(listOf("a.jpg", "b.jpg")))
        assertNull(DeviceCoverSources.folderCover(listOf("01.flac")))
    }

    @Test
    fun decodedNoSmallerThanAsked() {
        assertEquals(1, CoverImages.sampleSize(1200, 1200, 800))
        assertEquals(2, CoverImages.sampleSize(1200, 1200, 600))
        assertEquals(16, CoverImages.sampleSize(3000, 3000, 120))
        assertEquals(1, CoverImages.sampleSize(100, 100, 400))
    }
}
