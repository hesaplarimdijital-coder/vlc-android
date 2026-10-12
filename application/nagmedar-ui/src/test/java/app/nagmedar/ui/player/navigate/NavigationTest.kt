package app.nagmedar.ui.player.navigate

import app.nagmedar.ui.player.ChapterMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The medialibrary of the tests: a list, and whether it takes bookmarks. */
internal class FakeBookmarkStore(var accepts: Boolean = true) : BookmarkStore {
    val kept = mutableListOf<BookmarkMark>()

    override suspend fun load(): List<BookmarkMark> = kept.toList()

    override suspend fun add(timeMs: Long, name: String): Boolean {
        if (!accepts) return false
        kept += BookmarkMark(timeMs, name)
        return true
    }

    override suspend fun rename(timeMs: Long, name: String): Boolean {
        val index = kept.indexOfFirst { it.timeMs == timeMs }
        if (index < 0) return false
        kept[index] = kept[index].copy(name = name)
        return true
    }

    override suspend fun remove(timeMs: Long): Boolean = kept.removeAll { it.timeMs == timeMs }
}

/**
 * S-608: "Jump to time" reads a film's times as people write them; bookmarks are added where the
 * film is, once a second, renamed and removed in the library; Shift+N and Shift+P step chapters.
 */
class NavigationTest {

    private val now = 4_305_000L // 1:11:45
    private val length = 8_290_000L // 2:18:10

    private fun jump(text: String) = parseJumpTime(text, now, length)

    @Test
    fun clockTimes() {
        assertEquals(JumpTarget.To(4_353_000), jump("1:12:33"))
        assertEquals(JumpTarget.To(4_353_000), jump("72:33"))
        assertEquals(JumpTarget.To(45_500), jump("0:45,5"))
        assertEquals(JumpTarget.To(45_500), jump("0:45.5"))
        assertEquals(JumpTarget.To(0), jump("0:00"))
        assertEquals(JumpTarget.To(length), jump("2:18:10"))
        // minutes and seconds under the hours stay under sixty
        assertEquals(JumpTarget.Unreadable, jump("1:60:00"))
        assertEquals(JumpTarget.Unreadable, jump("1:12:75"))
        assertEquals(JumpTarget.Unreadable, jump("1:2:3:4"))
        assertEquals(JumpTarget.Unreadable, jump(":30"))
    }

    @Test
    fun numbersWithUnits() {
        assertEquals(JumpTarget.To(45_000), jump("45"))
        assertEquals(JumpTarget.To(45_000), jump("45 sn"))
        assertEquals(JumpTarget.To(720_000), jump("12 dk"))
        assertEquals(JumpTarget.To(4_320_000), jump("1 sa 12 dk"))
        assertEquals(JumpTarget.To(4_353_000), jump("1h12m33s"))
        assertEquals(JumpTarget.To(90_000), jump("1,5 dk"))
        assertEquals(JumpTarget.To(90_000), jump("90s"))
        // a number without a unit among numbers with one says nothing
        assertEquals(JumpTarget.Unreadable, jump("1 sa 30"))
        assertEquals(JumpTarget.Unreadable, jump("45x"))
        assertEquals(JumpTarget.Unreadable, jump(""))
        assertEquals(JumpTarget.Unreadable, jump("   "))
        assertEquals(JumpTarget.Unreadable, jump("bölüm"))
    }

    @Test
    fun fromNowAndShares() {
        assertEquals(JumpTarget.To(now + 30_000), jump("+30"))
        assertEquals(JumpTarget.To(now - 120_000), jump("−2 dk"))
        assertEquals(JumpTarget.To(now - 120_000), jump("-2:00"))
        // before the start is the start
        assertEquals(JumpTarget.To(0), jump("-3 sa"))
        assertEquals(JumpTarget.To(length / 2), jump("%50"))
        assertEquals(JumpTarget.To(length / 2), jump("50%"))
        assertEquals(JumpTarget.To(now + length / 10), jump("+%10"))
        assertEquals(JumpTarget.Unreadable, jump("%150"))
        // a stream's length is unknown: no share of it, any time
        assertEquals(JumpTarget.Unreadable, parseJumpTime("%50", now, 0))
        assertEquals(JumpTarget.To(36_000_000), parseJumpTime("10:00:00", now, 0))
    }

    @Test
    fun pastTheEndIsSaid() {
        assertEquals(JumpTarget.PastEnd(length), jump("3:00:00"))
        assertEquals(JumpTarget.PastEnd(length), jump("+2 sa"))
    }

    private fun controller(store: FakeBookmarkStore = FakeBookmarkStore()) = BookmarksController(store, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun bookmarksAreAddedOnceASecondInTimeOrder() {
        val store = FakeBookmarkStore()
        val bookmarks = controller(store)
        val first = bookmarks.add(4_150_000) { "Yer imi $it" }
        assertEquals(BookmarkMark(4_150_000, "Yer imi 1"), first)
        bookmarks.add(1_200_000) { "Yer imi $it" }
        // within a second of one: that one
        assertEquals(first, bookmarks.add(4_150_600) { "Yer imi $it" })
        assertEquals(listOf(1_200_000L, 4_150_000L), bookmarks.marks.map { it.timeMs })
        assertEquals(listOf("Yer imi 2", "Yer imi 1"), bookmarks.marks.map { it.name })
        assertEquals(2, store.kept.size)
        assertFalse(bookmarks.refused)
    }

    @Test
    fun aFilmThatCannotKeepThemSaysSo() {
        val store = FakeBookmarkStore(accepts = false)
        val bookmarks = controller(store)
        bookmarks.add(60_000) { "Yer imi $it" }
        assertTrue(bookmarks.refused)
        assertTrue(bookmarks.marks.isEmpty())
    }

    @Test
    fun renamedAndRemovedInTheLibrary() {
        val store = FakeBookmarkStore()
        val bookmarks = controller(store)
        val mark = bookmarks.add(4_150_000) { "Yer imi $it" }
        bookmarks.rename(mark, "  Kuzey ışıkları  ")
        assertEquals("Kuzey ışıkları", bookmarks.marks.single().name)
        assertEquals("Kuzey ışıkları", store.kept.single().name)
        // an empty name keeps the old one
        bookmarks.rename(bookmarks.marks.single(), " ")
        assertEquals("Kuzey ışıkları", store.kept.single().name)
        bookmarks.remove(bookmarks.marks.single())
        assertTrue(bookmarks.marks.isEmpty())
        assertTrue(store.kept.isEmpty())
    }

    @Test
    fun anotherFilmReadsItsOwn() {
        val store = FakeBookmarkStore()
        val bookmarks = controller(store)
        store.kept += BookmarkMark(90_000, "Açılış")
        store.kept += BookmarkMark(30_000, "Jenerik")
        bookmarks.refresh()
        assertEquals(listOf("Jenerik", "Açılış"), bookmarks.marks.map { it.name })
        assertEquals(90_000L, bookmarks.next(30_000)?.timeMs)
        assertEquals(30_000L, bookmarks.previous(90_000)?.timeMs)
        // just after a jump to one, the next is the one after it
        assertNull(bookmarks.next(90_400))
        assertNull(bookmarks.previous(30_500))
    }

    @Test
    fun chaptersStepForwardAndBack() {
        val chapters = listOf(ChapterMark(0, "Açılış"), ChapterMark(1_200_000, "Kuzey"), ChapterMark(4_000_000, "Fırtına"))
        assertEquals(2, chapterStep(chapters, 2_000_000, forward = true))
        // the last one has none after it
        assertNull(chapterStep(chapters, now, forward = true))
        // some way into a chapter, back is its start; at its start, the one before
        assertEquals(2, chapterStep(chapters, now, forward = false))
        assertEquals(1, chapterStep(chapters, 4_001_000, forward = false))
        assertEquals(0, chapterStep(chapters, 500, forward = false))
        assertNull(chapterStep(emptyList(), now, forward = true))
    }
}
