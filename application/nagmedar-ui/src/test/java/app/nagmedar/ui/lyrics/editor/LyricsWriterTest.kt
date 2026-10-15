package app.nagmedar.ui.lyrics.editor

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.LyricsSource
import app.nagmedar.ui.lyrics.LyricLine
import app.nagmedar.ui.lyrics.Lyrics
import app.nagmedar.ui.lyrics.LyricsOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-1005: saving beside the file or into its tags, with the lyrics replaced kept. */
@RunWith(AndroidJUnit4::class)
class LyricsWriterTest {

    private val cache = MemoryLyricsCache()
    private val sidecar = FakeSidecar()
    private val tags = FakeTags()
    private val writer = LyricsWriter(cache, sidecar, tags) { 1234 }
    private val lines = listOf(LyricLine(1_000, "bir"), LyricLine(2_000, "iki"))
    private val lrc = "[ti:Parça]\n[ar:Sanatçı]\n[al:Albüm]\n[length:03:35]\n[00:01.00]bir\n[00:02.00]iki"

    @Test
    fun savingBesideTheFileWritesTheLrcWithItsTagsAndKeepsItAsTheUsers() {
        val result = writer.toFile(SAMPLE_TRACK, lines, LrcHeader("Parça", "Sanatçı", "Albüm", 215_400))
        assertEquals(SaveResult.Saved("Parça.lrc"), result)
        assertEquals(lrc, sidecar.written)
        assertEquals(lrc, cache.text(SAMPLE_TRACK, LyricsSource.USER))
        assertEquals(1234L, cache.rows[SAMPLE_TRACK.key to LyricsSource.USER]!!.fetchedAt)
        // nothing was there to keep
        assertNull(cache.text(SAMPLE_TRACK, LyricsSource.ORIGINAL_FILE))
    }

    @Test
    fun theLrcThatWasThereIsKeptOnceAndNeverOverwritten() {
        sidecar.text = "[00:05.00]eski"
        writer.toFile(SAMPLE_TRACK, lines, null)
        assertEquals("[00:05.00]eski", cache.text(SAMPLE_TRACK, LyricsSource.ORIGINAL_FILE))
        // the file now holds the first save; a second save does not make that the original
        writer.toFile(SAMPLE_TRACK, listOf(LyricLine(3_000, "üç")), null)
        assertEquals("[00:05.00]eski", cache.text(SAMPLE_TRACK, LyricsSource.ORIGINAL_FILE))
        assertEquals("[00:03.00]üç", cache.text(SAMPLE_TRACK, LyricsSource.USER))
        assertEquals(listOf(OriginalLyrics(LyricsSource.ORIGINAL_FILE, "[00:05.00]eski")), writer.originals(SAMPLE_TRACK))
    }

    @Test
    fun withoutPermissionTheUserIsToldToPickAFolderAndTheNextSaveGoesThere() {
        sidecar.result = { folder -> if (folder == null) SidecarResult.NeedsFolder else SidecarResult.Written("Parça.lrc") }
        assertEquals(SaveResult.NeedsFolder, writer.toFile(SAMPLE_TRACK, lines, null))
        assertNull(cache.text(SAMPLE_TRACK, LyricsSource.USER))
        val tree = Uri.parse("content://tree/primary%3AMusic")
        assertEquals(SaveResult.Saved("Parça.lrc"), writer.toFile(SAMPLE_TRACK, lines, null, tree))
        assertEquals(tree, sidecar.folder)
    }

    @Test
    fun aFailedWriteLeavesNoUserLyrics() {
        sidecar.result = { SidecarResult.Failed }
        assertEquals(SaveResult.Failed, writer.toFile(SAMPLE_TRACK, lines, null))
        assertNull(cache.text(SAMPLE_TRACK, LyricsSource.USER))
    }

    @Test
    fun savingIntoTheTagsKeepsTheTagsLyricsAsTheOriginal() {
        tags.lyrics = Lyrics.of("Eski söz\nikinci", LyricsOrigin.Embedded)
        assertEquals(SaveResult.Saved(null), writer.toTag(SAMPLE_TRACK, lines))
        assertEquals(lines, tags.written)
        assertEquals("Eski söz\nikinci", cache.text(SAMPLE_TRACK, LyricsSource.ORIGINAL_TAG))
        assertEquals("[00:01.00]bir\n[00:02.00]iki", cache.text(SAMPLE_TRACK, LyricsSource.USER))
        // the second save finds the first one's lyrics in the tags: the original stays
        writer.toTag(SAMPLE_TRACK, listOf(LyricLine(9_000, "dokuz")))
        assertEquals("Eski söz\nikinci", cache.text(SAMPLE_TRACK, LyricsSource.ORIGINAL_TAG))
    }

    @Test
    fun aFileThatTakesNoTagsFails() {
        tags.writable = false
        assertEquals(SaveResult.Failed, writer.toTag(SAMPLE_TRACK, lines))
        assertNull(cache.text(SAMPLE_TRACK, LyricsSource.USER))
    }

    @Test
    fun theTagsAndTheFileKeepTheirOwnOriginals() {
        tags.lyrics = Lyrics.of("tag sözü", LyricsOrigin.Embedded)
        sidecar.text = "[00:01.00]dosya sözü"
        writer.toFile(SAMPLE_TRACK, lines, null)
        writer.toTag(SAMPLE_TRACK, lines)
        assertEquals(
            listOf(OriginalLyrics(LyricsSource.ORIGINAL_TAG, "tag sözü"), OriginalLyrics(LyricsSource.ORIGINAL_FILE, "[00:01.00]dosya sözü")),
            writer.originals(SAMPLE_TRACK),
        )
        assertTrue(cache.rows[SAMPLE_TRACK.key to LyricsSource.ORIGINAL_FILE]!!.synced)
    }
}
