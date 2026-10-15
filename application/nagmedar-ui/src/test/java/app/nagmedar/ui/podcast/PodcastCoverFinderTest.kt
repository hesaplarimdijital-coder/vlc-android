package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.covers.CoverOrigin
import app.nagmedar.ui.covers.CoverStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.IOException

/** S-1101: shows' covers, which are web addresses: fetched once, kept, and not asked for again while the server had none. */
@RunWith(AndroidJUnit4::class)
class PodcastCoverFinderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(20) { 1 }
    private var answer: ByteArray? = png
    private var failure: IOException? = null
    private val asked = mutableListOf<String>()
    private var now = 1_760_000_000_000L

    private fun finder() = PodcastCoverFinder(
        CoverStore(folder.root),
        { url ->
            asked += url
            failure?.let { throw it }
            answer
        },
        clock = { now },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun fetchedOnceThenKept() = runBlocking {
        val finder = finder()
        val first = finder.find("https://example.org/kapak.jpg")!!
        assertEquals(CoverOrigin.Online, first.origin)
        assertArrayEquals(png, first.file.readBytes())
        finder.find("https://example.org/kapak.jpg")
        assertEquals(1, asked.size)
    }

    @Test
    fun whatIsNoImageIsNotKeptAndNotAskedAgainForAWeek() = runBlocking {
        val finder = finder()
        answer = "<html>404</html>".toByteArray()
        assertNull(finder.find("https://example.org/yok.jpg"))
        assertNull(finder.find("https://example.org/yok.jpg"))
        assertEquals(1, asked.size)

        now += PodcastCoverFinder.MISS_RETRY_MS + 1
        answer = png
        assertNotNull(finder.find("https://example.org/yok.jpg"))
        assertEquals(2, asked.size)
    }

    @Test
    fun noSuchImageIsAMiss() = runBlocking {
        val finder = finder()
        answer = null
        assertNull(finder.find("https://example.org/404.jpg"))
        assertNull(finder.find("https://example.org/404.jpg"))
        assertEquals(1, asked.size)
    }

    @Test
    fun aNetworkFailureIsNotRemembered() = runBlocking {
        val finder = finder()
        failure = IOException("down")
        assertNull(finder.find("https://example.org/kapak.jpg"))
        failure = null
        assertNotNull(finder.find("https://example.org/kapak.jpg"))
        assertEquals(2, asked.size)
    }
}
