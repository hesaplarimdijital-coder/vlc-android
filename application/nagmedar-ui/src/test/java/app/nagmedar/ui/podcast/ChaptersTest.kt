package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.PodcastEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** S-1104: chapters from Podcasting 2.0's JSON and from the ID3 tag of an MP3 (binary tags built here, frame by frame), and where they are kept. */
@RunWith(AndroidJUnit4::class)
class ChaptersTest {

    // ------------------------------------------------------------ Podcasting 2.0

    @Test
    fun aChaptersFileReadsInTheOrderOfItsTimes() {
        val chapters = Chapters.parseJson(
            """
            {"version": "1.2.0", "chapters": [
              {"startTime": 125.5, "title": "Neva Kâr", "img": "https://cdn.example.org/neva.jpg", "url": "https://example.org/neva"},
              {"startTime": 0, "title": "Giriş"},
              {"startTime": 61, "title": "Itrî", "endTime": 125}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("Giriş", "Itrî", "Neva Kâr"), chapters.map { it.title })
        assertEquals(listOf(0L, 61_000L, 125_500L), chapters.map { it.startMs })
        assertEquals(125_000L, chapters[1].endMs)
        assertEquals("https://cdn.example.org/neva.jpg", chapters[2].imageUrl)
        assertEquals("https://example.org/neva", chapters[2].url)
    }

    @Test
    fun chaptersNotForTheListAreLeftOut() {
        val chapters = Chapters.parseJson("""{"chapters": [{"startTime": 0, "title": "A"}, {"startTime": 5, "title": "reklam", "toc": false}, {"startTime": 9, "title": "B", "toc": true}]}""")
        assertEquals(listOf("A", "B"), chapters.map { it.title })
    }

    @Test
    fun anAddressThatIsNotHttpIsNotKept() {
        val chapters = Chapters.parseJson("""{"chapters": [{"startTime": 0, "title": "A", "img": "javascript:alert(1)", "url": "file:///etc/passwd"}]}""")
        assertNull(chapters.single().imageUrl)
        assertNull(chapters.single().url)
    }

    @Test
    fun aBareArrayAndAByteOrderMarkRead() {
        assertEquals(2, Chapters.parseJson("﻿" + """[{"startTime": 0, "title": "A"}, {"startTime": 3, "title": "B"}]""").size)
    }

    @Test
    fun whatIsNoChaptersFileIsEmpty() {
        assertTrue(Chapters.parseJson("").isEmpty())
        assertTrue(Chapters.parseJson("<html>").isEmpty())
        assertTrue(Chapters.parseJson("""{"chapters": "yok"}""").isEmpty())
        assertTrue(Chapters.parseJson("""{"chapters": [{"title": "zamansız"}, {"startTime": -4, "title": "eksi"}, 7, null]}""").isEmpty())
    }

    @Test
    fun twoChaptersAtOneTimeAreOne() {
        assertEquals(listOf("A"), Chapters.parseJson("""{"chapters": [{"startTime": 3, "title": "A"}, {"startTime": 3, "title": "B"}]}""").map { it.title })
    }

    // ------------------------------------------------------------ ID3

    /** A tag of version [version] ([3] or [4]) holding [frames]. */
    private fun tag(version: Int, vararg frames: ByteArray, flags: Int = 0, padding: Int = 8): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach { body.write(it) }
        body.write(ByteArray(padding))
        val bytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray())
        out.write(version)
        out.write(0)
        out.write(flags)
        out.write(syncsafe(bytes.size))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun syncsafe(value: Int) = byteArrayOf((value shr 21 and 0x7F).toByte(), (value shr 14 and 0x7F).toByte(), (value shr 7 and 0x7F).toByte(), (value and 0x7F).toByte())

    private fun bigEndian(value: Long) = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun frame(version: Int, id: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(if (version == 4) syncsafe(body.size) else bigEndian(body.size.toLong()))
        out.write(byteArrayOf(0, 0))
        out.write(body)
        return out.toByteArray()
    }

    private fun text(version: Int, id: String, value: String, encoding: Int = 3): ByteArray {
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }
        return frame(version, id, byteArrayOf(encoding.toByte()) + value.toByteArray(charset))
    }

    private fun chap(version: Int, id: String, startMs: Long, endMs: Long, vararg sub: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(id.toByteArray(Charsets.ISO_8859_1))
        body.write(0)
        body.write(bigEndian(startMs))
        body.write(bigEndian(endMs))
        body.write(bigEndian(0xFFFFFFFFL))
        body.write(bigEndian(0xFFFFFFFFL))
        sub.forEach { body.write(it) }
        return frame(version, "CHAP", body.toByteArray())
    }

    private fun toc(version: Int, id: String, topLevel: Boolean, vararg children: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(id.toByteArray(Charsets.ISO_8859_1))
        body.write(0)
        body.write(if (topLevel) 0x03 else 0x01)
        body.write(children.size)
        children.forEach {
            body.write(it.toByteArray(Charsets.ISO_8859_1))
            body.write(0)
        }
        return frame(version, "CTOC", body.toByteArray())
    }

    @Test
    fun v24ChaptersReadWithTheirTitlesAndTimes() {
        val bytes = tag(
            4,
            chap(4, "ch0", 0, 61_000, text(4, "TIT2", "Giriş")),
            chap(4, "ch1", 61_000, 125_000, text(4, "TIT2", "Itrî ve mirası")),
            chap(4, "ch2", 125_000, 3_600_000, text(4, "TIT2", "Neva Kâr"), frame(4, "WXXX", byteArrayOf(0) + byteArrayOf(0) + "https://example.org/neva".toByteArray())),
        )
        val chapters = Chapters.parseId3(bytes + ByteArray(100))
        assertEquals(listOf("Giriş", "Itrî ve mirası", "Neva Kâr"), chapters.map { it.title })
        assertEquals(listOf(0L, 61_000L, 125_000L), chapters.map { it.startMs })
        assertEquals(125_000L, chapters[1].endMs)
        assertEquals("https://example.org/neva", chapters[2].url)
        assertNull(chapters[0].url)
    }

    @Test
    fun v23ReadsToo_andTitlesInOtherEncodings() {
        val bytes = tag(
            3,
            chap(3, "a", 0, 10_000, text(3, "TIT2", "Önsöz", encoding = 0)),
            chap(3, "b", 10_000, 20_000, text(3, "TIT2", "Gece Yarısı", encoding = 1)),
        )
        assertEquals(listOf("Önsöz", "Gece Yarısı"), Chapters.parseId3(bytes).map { it.title })
    }

    @Test
    fun theTableOfContentsGivesTheOrder() {
        val bytes = tag(
            4,
            toc(4, "toc", topLevel = true, "ch2", "ch0", "ch1"),
            chap(4, "ch0", 0, 10, text(4, "TIT2", "A")),
            chap(4, "ch1", 10, 20, text(4, "TIT2", "B")),
            chap(4, "ch2", 20, 30, text(4, "TIT2", "C")),
        )
        assertEquals(listOf("C", "A", "B"), Chapters.parseId3(bytes).map { it.title })
    }

    @Test
    fun withoutATableOfContentsTheTimesGiveTheOrder() {
        val bytes = tag(4, chap(4, "late", 5_000, 9_000, text(4, "TIT2", "Sonra")), chap(4, "early", 0, 5_000, text(4, "TIT2", "Önce")))
        assertEquals(listOf("Önce", "Sonra"), Chapters.parseId3(bytes).map { it.title })
    }

    @Test
    fun aChapterWithoutATitleHasAnEmptyOne() {
        val bytes = tag(4, chap(4, "a", 0, 10))
        assertEquals("", Chapters.parseId3(bytes).single().title)
    }

    @Test
    fun otherFramesBetweenTheChaptersAreSkipped() {
        val bytes = tag(4, text(4, "TIT2", "Bölüm adı"), text(4, "TPE1", "Sunucu"), chap(4, "a", 0, 10, text(4, "TIT2", "A")), text(4, "TALB", "Albüm"))
        assertEquals(listOf("A"), Chapters.parseId3(bytes).map { it.title })
    }

    @Test
    fun aTagCutShortKeepsTheChaptersBeforeTheCut() {
        val whole = tag(4, chap(4, "a", 0, 10, text(4, "TIT2", "A")), chap(4, "b", 10, 20, text(4, "TIT2", "B")))
        val cut = whole.copyOf(whole.size - 20)
        assertEquals(listOf("A"), Chapters.parseId3(cut).map { it.title })
    }

    @Test
    fun unsynchronisationIsUndone() {
        // a title with a 0xFF byte followed by a zero (written 0xFF 0x00 0x00): "ÿ" in ISO-8859-1
        val title = frame(4, "TIT2", byteArrayOf(0, 0xFF.toByte(), 0x41))
        val plain = tag(4, chap(4, "a", 0, 10, title))
        val body = plain.copyOfRange(10, plain.size)
        val unsynced = ByteArrayOutputStream()
        body.forEach {
            unsynced.write(it.toInt())
            if (it == 0xFF.toByte()) unsynced.write(0)
        }
        val bytes = plain.copyOfRange(0, 5) + byteArrayOf(0x80.toByte()) + syncsafe(unsynced.size()) + unsynced.toByteArray()
        assertEquals("ÿA", Chapters.parseId3(bytes).single().title)
    }

    @Test
    fun noTagNoChapters() {
        assertTrue(Chapters.parseId3(ByteArray(0)).isEmpty())
        assertTrue(Chapters.parseId3("RIFF....WAVE".toByteArray()).isEmpty())
        assertTrue(Chapters.parseId3(tag(2, ByteArray(0))).isEmpty())
        assertTrue(Chapters.parseId3(tag(4, text(4, "TIT2", "Yalnız başlık"))).isEmpty())
    }

    @Test
    fun theTagsSizeIsToldFromItsHeader() {
        val bytes = tag(4, chap(4, "a", 0, 10, text(4, "TIT2", "A")))
        assertEquals(bytes.size, Chapters.id3Size(bytes.copyOf(10)))
        assertNull(Chapters.id3Size("RIFF000000".toByteArray()))
        assertNull(Chapters.id3Size(ByteArray(4)))
    }

    // ------------------------------------------------------------ moving between chapters

    private val three = listOf(Chapter(0, "A"), Chapter(60_000, "B"), Chapter(120_000, "C"))

    @Test
    fun nextGoesToTheNextAndStopsAtTheLast() {
        assertEquals(1, Chapters.step(three, 10_000, forward = true))
        assertEquals(2, Chapters.step(three, 60_000, forward = true))
        assertNull(Chapters.step(three, 130_000, forward = true))
    }

    @Test
    fun previousGoesToTheStartOfThisOneThenTheOneBefore() {
        assertEquals(1, Chapters.step(three, 90_000, forward = false))
        assertEquals(0, Chapters.step(three, 61_000, forward = false))
        assertEquals(0, Chapters.step(three, 1_000, forward = false))
        assertNull(Chapters.step(emptyList(), 0, forward = false))
    }

    @Test
    fun theChapterPlayingIsTheLastThatStarted() {
        assertEquals(1, Chapters.indexAt(three, 60_000))
        assertEquals(2, Chapters.indexAt(three, 999_000))
        assertNull(Chapters.indexAt(listOf(Chapter(5_000, "geç")), 1_000))
    }

    // ------------------------------------------------------------ kept

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theCacheFolderIsAskedForOnTheExtrasThreadNotWhenTheyAreMade() {
        // the app's cacheDir looks at the disk: the episode pages make the extras on the main thread
        var asked = 0
        val dir = folder.newFolder("podcast")
        val source = object : ExtraSource {
            override suspend fun bytes(url: String, limit: Int, offset: Long) = """{"chapters": [{"startTime": 0, "title": "Giriş"}]}""".toByteArray()

            override fun local(location: String, limit: Int): ByteArray? = null
        }
        val extras = EpisodeExtras(source, { asked++; dir }, io = Dispatchers.Unconfined)
        assertEquals(0, asked)
        val episode = PodcastEpisode(feedId = 1, id = 3, guid = "gece-3", title = "Üçüncü gece", audioUrl = "https://cdn.example.org/gece/3.mp3", chaptersUrl = "https://cdn.example.org/gece/3.json")
        val chapters = runBlocking { extras.chapters(episode) }
        assertEquals(listOf("Giriş"), (chapters as ExtraLoad.Ready).value.map { it.title })
        assertEquals(1, asked)
        assertTrue(File(dir, "chapters-3.json").isFile)
    }
}
