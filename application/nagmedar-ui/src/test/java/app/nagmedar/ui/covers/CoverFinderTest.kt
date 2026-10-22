package app.nagmedar.ui.covers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** S-1006: where an album's cover comes from, what is kept of it, when Cover Art Archive is asked. */
class CoverFinderTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** The first bytes of a PNG, a JPEG and something else, padded. */
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(20) { 1 }
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(20) { 2 }
    private val other = jpeg.copyOf().also { it[24 - 1] = 3 }
    private val text = "not an image at all".toByteArray()

    private var inTags: ByteArray? = null
    private var inFolder: ByteArray? = null
    private var ids = MusicBrainzIds()
    private var answer: ByteArray? = null
    private var failure: IOException? = null
    private val asked = mutableListOf<CoverQuery>()
    private var online = true
    private var now = 1_760_000_000_000L

    private val sources = object : CoverSources {
        override fun tags(track: CoverTrack) = inTags

        override fun folder(track: CoverTrack) = inFolder

        override fun ids(track: CoverTrack) = ids
    }

    private val archive = CoverOnline { query ->
        asked += query
        failure?.let { throw it }
        answer
    }

    private val store by lazy { CoverStore(folder.newFolder("covers")) }
    private val finder by lazy { CoverFinder(store, sources, archive, online = { online }, clock = { now }, io = Dispatchers.Unconfined) }

    private val track = CoverTrack("file:///sdcard/Music/Cemre Kaya/Gece Yolcusu/01.flac", "Gece Yolcusu", "Cemre Kaya")

    private fun find(of: CoverTrack = track) = runBlocking { finder.find(of) }

    @Test
    fun theStoresFolderIsAskedForAtTheFirstReadNotWhenItIsMade() {
        // the app's folder (filesDir) looks at the disk: the finder is made on the main thread, reads off it
        var asked = 0
        val dir = folder.newFolder("later")
        val later = CoverStore { asked++; dir }
        assertEquals(0, asked)
        assertNull(later.kept(track.key))
        assertEquals(1, asked)
    }

    @Test
    fun theTagsThenTheFolderThenTheArchive() {
        inTags = png
        inFolder = jpeg
        answer = other
        assertEquals(CoverOrigin.Tags, find()!!.origin)
        // kept: the same album's other tracks share it, the file is not read again
        inTags = null
        val again = find(track.copy(uri = "file:///sdcard/Music/Cemre Kaya/Gece Yolcusu/02.flac"))!!
        assertEquals(CoverOrigin.Tags, again.origin)
        assertArrayEquals(png, again.file.readBytes())
        assertEquals(emptyList<CoverQuery>(), asked)
        // another album: its folder's
        val konuk = CoverTrack("file:///sdcard/Music/Deniz Su/Konuk/01.flac", "Konuk", "Deniz Su")
        assertEquals(CoverOrigin.Folder, find(konuk)!!.origin)
        // a third, with nothing on the device: Cover Art Archive's
        inFolder = null
        val third = CoverTrack("file:///sdcard/Music/Kadri Erdem/Itri/01.flac", "Itrî'nin İzinde", "Kadri Erdem")
        ids = MusicBrainzIds(releaseGroup = "6c2e2f0e-3a3d-4b8e-9f0e-8d7b2a1c0e11")
        assertEquals(CoverOrigin.Online, find(third)!!.origin)
        assertEquals(listOf(CoverQuery("Kadri Erdem", "Itrî'nin İzinde", ids)), asked)
    }

    @Test
    fun whatIsNotAnImageIsPassedOver() {
        inTags = text
        inFolder = jpeg
        assertEquals(CoverOrigin.Folder, find()!!.origin)
        assertTrue(looksLikeImage(png))
        assertTrue(looksLikeImage(jpeg))
        assertFalse(looksLikeImage(text))
        assertFalse(looksLikeImage(ByteArray(4)))
    }

    @Test
    fun theArchiveIsAskedOnlyWhenItMayBeAndAgainAfterAWeek() {
        online = false
        answer = png
        assertNull(find())
        // without an artist there is nothing to ask for
        online = true
        assertNull(find(track.copy(artist = null)))
        assertEquals(0, asked.size)
        // nothing found: asked again a week later, not before
        answer = null
        assertNull(find())
        now += 6L * 24 * 60 * 60 * 1000
        assertNull(find())
        assertEquals(1, asked.size)
        now += 2L * 24 * 60 * 60 * 1000
        answer = png
        assertEquals(CoverOrigin.Online, find()!!.origin)
        assertEquals(2, asked.size)
    }

    @Test
    fun offlineIsNotAMiss() {
        failure = IOException("offline")
        assertNull(find())
        failure = null
        answer = png
        assertEquals(CoverOrigin.Online, find()!!.origin)
    }

    @Test
    fun chosenByHand() {
        inTags = png
        find()
        val chosen = runBlocking { finder.choose(track, jpeg) }!!
        assertEquals(CoverOrigin.User, chosen.origin)
        assertEquals(CoverOrigin.User, find()!!.origin)
        assertTrue(runBlocking { finder.isChosen(track) })
        // not an image: not taken
        assertNull(runBlocking { finder.choose(track, text) })
        // looked for again: what was found goes, the chosen one stays
        inTags = null
        inFolder = other
        assertEquals(CoverOrigin.User, runBlocking { finder.again(track) }!!.origin)
        runBlocking { finder.unchoose(track) }
        assertEquals(CoverOrigin.Folder, find()!!.origin)
        assertFalse(runBlocking { finder.isChosen(track) })
    }

    @Test
    fun clearedButTheChosenStay() {
        inTags = png
        find()
        find(CoverTrack("file:///a/b/x.flac", "Konuk", "Deniz Su"))
        runBlocking { finder.choose(CoverTrack("file:///c/d/y.flac", "Tek", "Çağla Akın"), jpeg) }
        assertEquals(2, runBlocking { finder.foundCount() })
        assertEquals(2, runBlocking { finder.clearFound() })
        assertEquals(0, runBlocking { finder.foundCount() })
        assertEquals(CoverOrigin.User, find(CoverTrack("file:///c/d/z.flac", "Tek", "Çağla Akın"))!!.origin)
    }

    /** S-1502: the covers chosen by hand go into a backup, and come back on another device. */
    @Test
    fun theChosenCoversGoIntoABackupAndComeBack() {
        runBlocking { finder.choose(track, png) }
        // one found: it is found again, not backed up
        inTags = jpeg
        find(CoverTrack("file:///a/b/x.flac", "Konuk", "Deniz Su"))
        val chosen = runBlocking { finder.chosenFiles() }
        assertEquals(1, chosen.size)
        val there = CoverFinder(CoverStore(folder.newFolder("there")), sources, archive, online = { false }, clock = { now }, io = Dispatchers.Unconfined)
        assertTrue(runBlocking { there.restoreChosen(chosen[0].name, chosen[0].readBytes()) })
        inTags = null
        assertEquals(CoverOrigin.User, runBlocking { there.find(track) }!!.origin)
        // not a chosen cover's name, not an image: not taken
        assertFalse(runBlocking { there.restoreChosen("../evil.user", png) })
        assertFalse(runBlocking { there.restoreChosen(chosen[0].name, text) })
    }

    @Test
    fun theKeys() {
        // an album's tracks share it, whatever the case or the spaces
        assertEquals(track.key, CoverTrack("file:///x.flac", " gece yolcusu ", "CEMRE KAYA").key)
        // a track without an album has its own
        assertEquals("file|file:///x.flac", CoverTrack("file:///x.flac", null, "Cemre Kaya").key)
        assertFalse(CoverTrack("file:///x.flac", "", "Cemre Kaya").searchable)
    }
}
