package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/** S-1102: OPML files as other apps write them, and ours written and read back. */
@RunWith(AndroidJUnit4::class)
class OpmlTest {

    private fun read(name: String): OpmlDocument = Opml.parse(Fixtures.stream(name))

    private fun problem(bytes: ByteArray): OpmlProblem = try {
        Opml.parse(ByteArrayInputStream(bytes))
        fail("read as OPML")
        throw AssertionError()
    } catch (e: OpmlException) {
        e.problem
    }

    @Test
    fun aFileLargerThanTheLimitIsTooLarge() {
        val outlines = (0 until 3_000).joinToString("\n") { """<outline type="rss" text="Yayın $it" xmlUrl="https://show$it.example.org/rss"/>""" }
        val bytes = """<?xml version="1.0"?><opml version="2.0"><head><title>x</title></head><body>$outlines</body></opml>""".toByteArray()
        // within the limit it reads; a limit smaller than the file refuses it, with its own reason
        assertEquals(3_000, Opml.parse(ByteArrayInputStream(bytes)).feeds.size)
        try {
            Opml.parse(ByteArrayInputStream(bytes), maxBytes = bytes.size - 100L)
            fail("read")
        } catch (e: OpmlException) {
            assertEquals(OpmlProblem.TooLarge, e.problem)
        }
        // what the limit is: megabytes, not a few kilobytes
        assertTrue(Opml.MAX_BYTES >= 2L * 1024 * 1024)
    }

    @Test
    fun aDoctypeFullOfEntitiesExpandsNothing() {
        val secret = java.io.File.createTempFile("secret", ".txt")
        try {
            secret.writeText("TOPSECRET-4711")
            val lols = (2..9).joinToString("\n") { n -> "<!ENTITY lol$n \"" + "&lol${n - 1};".repeat(10) + "\">" }
            val text = """<?xml version="1.0"?>
                <!DOCTYPE opml [<!ENTITY lol "lol">$lols<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
                <opml version="2.0"><head><title>&xxe;</title></head><body>
                <outline type="rss" text="&lol9;&xxe;" xmlUrl="https://example.org/feed"/>
                </body></opml>""".trimIndent()
            val started = System.nanoTime()
            val list = try {
                Opml.parse(ByteArrayInputStream(text.toByteArray()))
            } catch (e: OpmlException) {
                null
            }
            assertTrue("read at once", System.nanoTime() - started < 5_000_000_000L)
            if (list != null) {
                val all = list.title.orEmpty() + list.feeds.joinToString { it.title.orEmpty() }
                assertFalse(all, "TOPSECRET" in all)
                assertTrue(all, all.length < 200)
            }
        } finally {
            secret.delete()
        }
    }

    @Test
    fun antennaPod() {
        val list = read("antennapod.opml")
        assertEquals("AntennaPod Subscriptions", list.title)
        assertEquals(0, list.skipped)
        assertEquals(
            listOf(
                OpmlFeed("https://feeds.example.org/gece.xml", "Gece Yayını", "https://gece.example.org"),
                // &amp; in an attribute is read once
                OpmlFeed("https://feeds.example.org/bilim?format=rss&lang=tr", "Bilim & Teknik", "https://bilim.example.org"),
                OpmlFeed("http://kitap.example.org/rss", "Kitap Kulübü", null),
            ),
            list.feeds,
        )
    }

    @Test
    fun pocketCastsKeepsItsFeedsInAFolder() {
        val list = read("pocketcasts.opml")
        assertEquals("Pocket Casts Feeds", list.title)
        assertEquals(
            listOf("https://feeds.example.org/gece.xml", "https://yolculuk.example.org/feed", "https://tarih.example.org/rss.xml"),
            list.feeds.map { it.feedUrl },
        )
        assertEquals(listOf("Gece Yayını", "Yolculuk", "Tarih Sohbetleri"), list.feeds.map { it.title })
    }

    @Test
    fun podcastAddictWritesXmlUrlInAnyCaseAndFeedScheme() {
        val list = read("podcastaddict.opml")
        assertEquals("Podcast Addict Subscriptions", list.title)
        assertEquals(
            listOf("https://itri.example.org/feed.xml", "https://makam.example.org/feed.xml", "https://sabah.example.org/ozet.rss"),
            list.feeds.map { it.feedUrl },
        )
        assertEquals(listOf("Itrî'nin Mirası", "Makam Dersleri", "Sabah Özeti"), list.feeds.map { it.title })
        // an empty folder is a folder, not a mistake
        assertEquals(0, list.skipped)
    }

    @Test
    fun overcastListsEpisodesToo() {
        val list = read("overcast.opml")
        assertEquals(listOf("Gece Yayını", "Yolculuk"), list.feeds.map { it.title })
        assertEquals(0, list.skipped)
    }

    @Test
    fun quirks() {
        val list = read("quirky.opml")
        // no title in the head
        assertNull(list.title)
        assertEquals(
            listOf("Türsüz", "Yalnız url", "İki kez", "Çift kaçış"),
            list.feeds.map { it.title },
        )
        // no type but xmlurl; type rss with only url
        assertEquals("https://tursuz.example.org/rss", list.feeds[0].feedUrl)
        assertEquals("https://yalniz.example.org/rss", list.feeds[1].feedUrl)
        // the double-escaped ampersand is one
        assertEquals("https://cift.example.org/rss?a=1&b=2", list.feeds[3].feedUrl)
        // no address, an address that is none, the same feed on http
        assertEquals(3, list.skipped)
    }

    @Test
    fun aListCutShortKeepsWhatCameBefore() {
        val list = read("truncated.opml")
        assertEquals(listOf("https://yarim.example.org/rss"), list.feeds.map { it.feedUrl })
    }

    @Test
    fun whatIsNoOpml() {
        assertEquals(OpmlProblem.NoFeeds, problem(Fixtures.bytes("empty.opml")))
        assertEquals(OpmlProblem.NotOpml, problem(Fixtures.bytes("feed.xml")))
        assertEquals(OpmlProblem.NotOpml, problem(Fixtures.bytes("not-a-feed.html")))
        assertEquals(OpmlProblem.NotOpml, problem("bir iki üç".toByteArray()))
        assertEquals(OpmlProblem.NotOpml, problem(ByteArray(0)))
        assertEquals(OpmlProblem.NotOpml, problem(byteArrayOf(0x1f, 0x8b.toByte(), 0x00, 0x01)))
    }

    @Test
    fun gzipAndByteOrderMark() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + Fixtures.bytes("pocketcasts.opml")
        assertEquals(3, Opml.parse(ByteArrayInputStream(bom)).feeds.size)
        assertEquals(3, Opml.parse(ByteArrayInputStream(Fixtures.gzip(Fixtures.bytes("pocketcasts.opml")))).feeds.size)
    }

    @Test
    fun aFeedListedTwiceIsListedOnce() {
        val list = read("overcast.opml")
        val twice = Opml.write(list.feeds + list.feeds, "x", 0)
        val back = Opml.parse(ByteArrayInputStream(twice.toByteArray()))
        assertEquals(list.feeds.size, back.feeds.size)
        assertEquals(list.feeds.size, back.skipped)
    }

    @Test
    fun whatWeWriteWeRead() {
        val feeds = listOf(
            OpmlFeed("https://feeds.example.org/gece.xml", "Gece Yayını", "https://gece.example.org"),
            OpmlFeed("https://feeds.example.org/bilim?format=rss&lang=tr", "Bilim & \"Teknik\" <1>", null),
            OpmlFeed("http://kitap.example.org/rss", null, null),
            OpmlFeed("https://example.org/ç?x=ü", "Itrî'nin Mirası", "https://itri.example.org/?a=1&b=2"),
        )
        val text = Opml.write(feeds, "Nagmedar", 1_791_612_000_000L)
        assertTrue(text, text.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">"))
        assertTrue(text, "<dateCreated>Sat, 10 Oct 2026 06:00:00 GMT</dateCreated>" in text)
        assertFalse(text, "& " in text)

        val back = Opml.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertEquals("Nagmedar", back.title)
        assertEquals(0, back.skipped)
        assertEquals(feeds.map { it.feedUrl }, back.feeds.map { it.feedUrl })
        assertEquals(listOf("Gece Yayını", "Bilim & \"Teknik\" <1>", "http://kitap.example.org/rss", "Itrî'nin Mirası"), back.feeds.map { it.title })
        assertEquals(listOf("https://gece.example.org", null, null, "https://itri.example.org/?a=1&b=2"), back.feeds.map { it.htmlUrl })
    }

    @Test
    fun theRealWorldFilesRoundTrip() {
        listOf("antennapod.opml", "pocketcasts.opml", "podcastaddict.opml", "overcast.opml", "quirky.opml").forEach { name ->
            val first = read(name)
            val second = Opml.parse(ByteArrayInputStream(Opml.write(first.feeds, "Nagmedar", 0).toByteArray()))
            assertEquals(name, first.feeds.map { it.feedUrl }, second.feeds.map { it.feedUrl })
        }
    }

    @Test
    fun charactersXmlCannotHoldAreDropped() {
        assertEquals("ab", Opml.escape("a\u0000\u0008b"))
        assertEquals("a&amp;b&lt;&gt;&quot;&apos;", Opml.escape("a&b<>\"'"))
        assertEquals("tab\tnew\nline", Opml.escape("tab\tnew\nline"))
        assertEquals("🎵", Opml.escape("🎵"))
        // a lone surrogate is no character
        assertEquals("ab", Opml.escape("a\uD83Cb"))
    }
}
