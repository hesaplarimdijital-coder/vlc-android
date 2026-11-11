package app.nagmedar.ui.library

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.0.3: the tracks played last, kept on the device by their ids for the next start's shelf: they read
 * back in their order, are found in the kept list of the Music (a track gone from it left out), and a
 * history that did not change is not written again.
 */
@RunWith(AndroidJUnit4::class)
class RecentKeptTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun track(id: Long, title: String): MediaWrapper =
        MLServiceLocator.getAbstractMediaWrapper(
            id, Uri.fromFile(java.io.File("/storage/emulated/0/Music/$title.flac")).toString(), 0L, 0f, 241_000L, MediaWrapper.TYPE_AUDIO, title, "$title.flac",
            7L, 7L, "Cemre Kaya", "Caz", 11L, "Kıyı", "Cemre Kaya", 0, 0, null, -2, -2, 3, 1, 1_760_000_000L, 0L, false, false, 2026, true, 1_760_000_100L,
        )

    private val music = listOf(track(1, "Gece Yolcusu"), track(2, "Şafak"), track(3, "Tek"), track(4, "Liman"))

    @After
    fun forget() {
        RecentKept.save(context, emptyList())
        RecentKept.forget()
    }

    @Test
    fun theIdsReadBackInTheirOrder() {
        assertEquals(listOf(4L, 1L, 3L), RecentKept.decode(RecentKept.encode(listOf(4L, 1L, 3L))))
        assertEquals(emptyList<Long>(), RecentKept.decode(RecentKept.encode(emptyList())))
        // what is not a number is left out
        assertEquals(listOf(4L, 3L), RecentKept.decode("4, x,3,"))
    }

    @Test
    fun theKeptTracksAreFoundInTheKeptListNewestFirst() {
        val shelf = RecentKept.tracks(listOf(4L, 1L, 3L), music)
        assertEquals(listOf("Liman", "Gece Yolcusu", "Tek"), shelf.map { it.title })
    }

    @Test
    fun aTrackGoneFromTheListIsLeftOutAndOneKeptTwiceComesOnce() {
        val shelf = RecentKept.tracks(listOf(9L, 2L, 2L, 1L), music)
        assertEquals(listOf("Şafak", "Gece Yolcusu"), shelf.map { it.title })
        assertTrue(RecentKept.tracks(emptyList(), music).isEmpty())
        assertTrue(RecentKept.tracks(listOf(1L), emptyList()).isEmpty())
    }

    @Test
    fun whatWasKeptIsThereAtTheNextStart() {
        RecentKept.forget()
        // nothing read yet: the first frame gets nothing and does not wait for the disk
        assertNull(RecentKept.cached())
        RecentKept.save(context, listOf(3L, 1L))
        // the next process: read from the disk, then in memory for the first frame
        RecentKept.forget()
        assertNull(RecentKept.cached())
        assertEquals(listOf(3L, 1L), RecentKept.read(context))
        assertEquals(listOf(3L, 1L), RecentKept.cached())
    }

    @Test
    fun aClearedHistoryLeavesNoShelf() {
        RecentKept.save(context, listOf(3L, 1L))
        RecentKept.save(context, emptyList())
        RecentKept.forget()
        assertEquals(emptyList<Long>(), RecentKept.read(context))
    }
}
