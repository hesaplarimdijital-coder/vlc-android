package app.nagmedar.ui.podcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/** S-1101: feeds as the world writes them: the good ones, the Podcasting 2.0 ones, the nasty ones, and what is no feed. */
@RunWith(AndroidJUnit4::class)
class FeedParserTest {

    @Test
    fun theTestFeed() {
        val feed = Fixtures.parse("feed.xml")
        assertEquals("Nağmedar Podcast", feed.title)
        assertEquals("Nağmedar", feed.author)
        assertEquals("https://example.org/nagmedar/cover.png", feed.imageUrl)
        assertEquals("https://example.org/nagmedar", feed.link)
        assertEquals("6a1b6e2b-8f1c-5c4e-9f4a-6d2b1f0e5a11", feed.podcastGuid)
        assertEquals(0, feed.skipped)
        val episode = feed.episodes.single()
        assertEquals("nagmedar-ep-1", episode.guid)
        assertEquals("Bölüm 1: Kayıpsız ses", episode.title)
        assertEquals("https://example.org/nagmedar/episode-01.mp3", episode.audioUrl)
        assertEquals("audio/mpeg", episode.mimeType)
        assertEquals(768_412L, episode.sizeBytes)
        assertEquals(60_000L, episode.durationMs)
        assertEquals(1_791_612_000_000L, episode.publishedAt)
        assertEquals("https://example.org/nagmedar/episode-01.chapters.json", episode.chaptersUrl)
    }

    @Test
    fun podcasting20AndItunesUnderOtherPrefixes() {
        val feed = Fixtures.parse("feed-full.xml")
        assertEquals("Gece Yayını", feed.title)
        // no itunes:author: the owner's name stands in
        assertEquals("Gece Ekibi", feed.author)
        assertEquals("Her hafta gece yarısı.\nMüzik, edebiyat & sohbet.", feed.description)
        // the itunes cover wins over the old RSS image
        assertEquals("https://gece.example.org/kapak.jpg", feed.imageUrl)
        assertEquals("https://gece.example.org", feed.link)
        // "pi:" is Podcast Index's namespace as the file declares it
        assertEquals("ead4c236-bf58-58c6-a2c6-a6b28d128cb6", feed.podcastGuid)
        assertEquals("https://gece.example.org/destek", feed.fundingUrl)
        assertEquals("Gece'yi destekleyin", feed.fundingMessage)

        // newest first
        assertEquals(listOf("gece-0002", "gece-0001"), feed.episodes.map { it.guid })
        val second = feed.episodes[0]
        assertEquals("İkinci gece: Itrî", second.title)
        assertEquals("https://cdn.example.org/gece/0002.m4a?src=feed", second.audioUrl)
        assertEquals("audio/x-m4a", second.mimeType)
        assertEquals(52_428_800L, second.sizeBytes)
        assertEquals(3_723_000L, second.durationMs)
        assertEquals(1_791_581_400_000L, second.publishedAt)
        assertEquals(2, second.season)
        assertEquals(2, second.episodeNumber)
        assertEquals("https://gece.example.org/0002.jpg", second.imageUrl)
        // the short description before the long notes
        assertEquals("Kısa özet.", second.description)
        assertEquals("https://gece.example.org/0002.chapters.json", second.chaptersUrl)
        // two transcripts: the subtitle file is the one worth having
        assertEquals("https://gece.example.org/0002.vtt", second.transcriptUrl)
        assertEquals("text/vtt", second.transcriptType)

        val first = feed.episodes[1]
        assertEquals(1_865_000L, first.durationMs)
        assertEquals("Özetin özeti.", first.description)
        assertNull(first.season)
        assertNull(first.chaptersUrl)
        assertNull(first.transcriptUrl)
    }

    @Test
    fun theNastyFeedIsReadForWhatItHas() {
        val feed = Fixtures.parse("feed-nasty.xml", "https://example.org/feeds/show.xml")
        assertEquals("Kötü Yayın & Ortağı", feed.title)
        assertEquals("Yazar", feed.author)
        val description = feed.description!!
        assertTrue(description, description.startsWith("Satır\nsonu ve boşluk"))
        assertTrue(description, "kalın üş" in description)
        assertFalse(description, '<' in description || '&' in description)

        // left out: no enclosure, a pdf, a guid seen before
        assertEquals(3, feed.skipped)
        assertEquals(listOf("relative", "html-title", "media", "bad-date"), feed.episodes.map { it.guid })

        val relative = feed.episodes[0]
        // no title: the file's name stands in; the address is made whole and its space encoded
        assertEquals("relative episode", relative.title)
        assertEquals("https://example.org/media/relative%20episode.mp3", relative.audioUrl)
        assertNull(relative.mimeType)
        assertEquals(0L, relative.sizeBytes)
        assertEquals(0L, relative.durationMs)
        assertEquals(1_791_180_300_000L, relative.publishedAt)

        val html = feed.episodes[1]
        assertEquals("Ham HTML başlık", html.title)
        assertEquals(1_791_090_000_000L, html.publishedAt)
        assertEquals(3_723_500L, html.durationMs)
        assertEquals("https://example.org/c.ogg", html.audioUrl)
        val notes = html.description!!
        assertTrue(notes, "bağlantı" in notes && "ham paragraf" in notes)
        assertFalse(notes, '<' in notes)

        // media:content without a declaration of its prefix, in EST
        val media = feed.episodes[2]
        assertEquals("https://example.org/d.mp3", media.audioUrl)
        assertEquals(2_048L, media.sizeBytes)
        assertEquals(1_791_025_200_000L, media.publishedAt)

        // a date nobody can read costs the episode its place in time, not its place in the list
        val broken = feed.episodes[3]
        assertEquals(0L, broken.publishedAt)
        assertEquals("Tarihi bozuk", broken.title)
        assertEquals("https://example.org/a.mp3", broken.audioUrl)
    }

    @Test
    fun theFirstOfTwoItemsWithOneGuidStays() {
        val feed = Fixtures.parse("feed-nasty.xml", "https://example.org/")
        assertEquals("https://example.org/a.mp3", feed.episodes.single { it.guid == "bad-date" }.audioUrl)
    }

    @Test
    fun atomWithEnclosureLinks() {
        val feed = Fixtures.parse("feed-atom.xml", "https://example.org/atom.xml")
        assertEquals("Atom Yayını", feed.title)
        assertEquals("Atom ile yayın.", feed.description)
        assertEquals("Atom Yazar", feed.author)
        assertEquals("https://example.org/", feed.link)
        assertEquals("https://example.org/logo.png", feed.imageUrl)
        assertEquals(1, feed.skipped)
        val episode = feed.episodes.single()
        assertEquals("tag:example.org,2026:1", episode.guid)
        assertEquals("Atom bölümü", episode.title)
        assertEquals("https://example.org/atom-1.mp3", episode.audioUrl)
        assertEquals("audio/mpeg", episode.mimeType)
        assertEquals(1_234L, episode.sizeBytes)
        // published, not updated
        assertEquals(1_791_374_400_000L, episode.publishedAt)
        assertEquals("Özet", episode.description)
    }

    @Test
    fun aFeedCutShortKeepsWhatCameBefore() {
        val feed = Fixtures.parse("feed-truncated.xml")
        assertEquals("Yarım Kalan", feed.title)
        assertEquals(listOf("full"), feed.episodes.map { it.guid })
    }

    @Test
    fun whatIsNotAFeed() {
        assertEquals(InvalidReason.NotFeed, Fixtures.invalidReason("not-a-feed.html"))
        assertEquals(InvalidReason.NotFeed, Fixtures.invalidReason("sitemap.xml"))
        assertEquals(InvalidReason.NotFeed, Fixtures.invalidReason("antennapod.opml"))
        assertEquals(InvalidReason.NoEpisodes, Fixtures.invalidReason("feed-no-episodes.xml"))
    }

    @Test
    fun whatIsNotXml() {
        assertEquals(InvalidReason.NotXml, Fixtures.reasonOf("Merhaba dünya, ben bir yayın değilim".toByteArray()))
        assertEquals(InvalidReason.NotXml, Fixtures.reasonOf("""{"feeds":[],"status":"true"}""".toByteArray()))
        assertEquals(InvalidReason.NotXml, Fixtures.reasonOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)))
        // gzip that is none
        assertEquals(InvalidReason.NotXml, Fixtures.reasonOf(byteArrayOf(0x1f, 0x8b.toByte(), 0x00, 0x01, 0x02, 0x03, 0x04)))
    }

    @Test
    fun nothingAtAll() {
        assertEquals(InvalidReason.Empty, Fixtures.reasonOf(ByteArray(0)))
        assertEquals(InvalidReason.Empty, Fixtures.reasonOf("  \r\n\t ".toByteArray()))
    }

    @Test
    fun gzipWhetherTheServerSaidSoOrNot() {
        val plain = Fixtures.parse("feed-full.xml")
        val unzipped = FeedParser.parse(ByteArrayInputStream(Fixtures.gzip(Fixtures.bytes("feed-full.xml"))))
        assertEquals(plain, unzipped)
    }

    @Test
    fun aByteOrderMarkAndBlankLinesBeforeTheDeclaration() {
        // feed-nasty.xml starts with both
        assertNotNull(Fixtures.parse("feed-nasty.xml", "https://example.org/").title)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + Fixtures.bytes("feed.xml")
        assertEquals("Nağmedar Podcast", FeedParser.parse(ByteArrayInputStream(bom)).title)
    }

    @Test
    fun aHugeFeedKeepsItsNewestEpisodes() {
        val feed = FeedParser.parse(ByteArrayInputStream(bigFeed(6_000).toByteArray()))
        assertEquals(FeedParser.MAX_EPISODES, feed.episodes.size)
        assertEquals(6_000 - FeedParser.MAX_EPISODES, feed.skipped)
        // the dates grow with the number: the newest is the last one written
        assertEquals("guid-5999", feed.episodes.first().guid)
        assertEquals("guid-1000", feed.episodes.last().guid)
    }

    @Test
    fun aFeedOfManyMoreItemsThanItKeepsIsTrimmedWhileItIsRead() {
        // past twice the limit the oldest are dropped on the way, so a million items never sit in memory at once
        val feed = FeedParser.parse(ByteArrayInputStream(bigFeed(12_000).toByteArray()))
        assertEquals(FeedParser.MAX_EPISODES, feed.episodes.size)
        assertEquals(12_000 - FeedParser.MAX_EPISODES, feed.skipped)
        assertEquals("guid-11999", feed.episodes.first().guid)
        assertEquals("guid-7000", feed.episodes.last().guid)
        assertEquals(FeedParser.MAX_EPISODES, feed.episodes.map { it.guid }.toSet().size)
    }

    @Test
    fun theNewestAreKeptWhateverTheOrderOfTheFeed() {
        // dates in a scrambled order, 12000 of them: the 5000 newest stay, whichever the document lists first
        val order = (0 until 12_000).sortedBy { (it * 7_919L) % 12_011 }
        val text = buildString {
            append("<rss version=\"2.0\"><channel><title>Karışık</title>")
            order.forEach { i ->
                append("<item><title>$i</title><guid>g$i</guid><pubDate>${1_577_836_800L + i * 60L}</pubDate>")
                append("<enclosure url=\"https://example.org/$i.mp3\" type=\"audio/mpeg\"/></item>")
            }
            append("</channel></rss>")
        }
        val feed = FeedParser.parse(ByteArrayInputStream(text.toByteArray()))
        assertEquals((11_999 downTo 7_000).map { "g$it" }, feed.episodes.map { it.guid })
    }

    @Test
    fun undatedItemsRankAfterDatedOnesAndKeepTheirOrder() {
        val text = buildString {
            append("<rss version=\"2.0\"><channel><title>Tarihsiz</title>")
            // 10000 with no date, then 3000 dated: the dated ones stay, and the undated fill the rest in the order of the document
            for (i in 0 until 13_000) {
                val date = if (i < 10_000) "" else "<pubDate>${1_577_836_800L + i * 60L}</pubDate>"
                append("<item><title>$i</title><guid>g$i</guid>$date<enclosure url=\"https://example.org/$i.mp3\" type=\"audio/mpeg\"/></item>")
            }
            append("</channel></rss>")
        }
        val ids = FeedParser.parse(ByteArrayInputStream(text.toByteArray())).episodes.map { it.guid }
        assertEquals((12_999 downTo 10_000).map { "g$it" } + (0 until 2_000).map { "g$it" }, ids)
    }

    private fun parseText(text: String): ParsedFeed? = try {
        FeedParser.parse(ByteArrayInputStream(text.toByteArray()))
    } catch (e: FeedFailure.Invalid) {
        // refusing the file is as safe as reading it without the entities
        null
    }

    @Test
    fun aBillionLaughsDoctypeExpandsNothing() {
        val lols = (2..9).joinToString("\n") { n -> "<!ENTITY lol$n \"" + "&lol${n - 1};".repeat(10) + "\">" }
        val text = """<?xml version="1.0"?>
            <!DOCTYPE lolz [<!ENTITY lol "lol">$lols]>
            <rss version="2.0"><channel><title>&lol9;</title>
            <item><title>Bölüm</title><description>&lol9;</description><guid>a</guid><enclosure url="https://example.org/a.mp3" type="audio/mpeg"/></item>
            </channel></rss>""".trimIndent()
        val started = System.nanoTime()
        val feed = parseText(text)
        assertTrue("read at once", System.nanoTime() - started < 5_000_000_000L)
        if (feed != null) {
            assertTrue(feed.title, feed.title.length < 100)
            assertTrue(feed.episodes.single().description.orEmpty().length < 100)
        }
    }

    @Test
    fun anExternalEntityIsNotRead() {
        val secret = java.io.File.createTempFile("secret", ".txt")
        try {
            secret.writeText("TOPSECRET-4711")
            val text = """<?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
                <rss version="2.0"><channel><title>&xxe;</title>
                <item><title>&xxe;</title><description>&xxe;</description><guid>a</guid><enclosure url="https://example.org/a.mp3" type="audio/mpeg"/></item>
                </channel></rss>""".trimIndent()
            val feed = parseText(text)
            if (feed != null) {
                val all = feed.title + feed.episodes.joinToString { it.title + it.description.orEmpty() }
                assertFalse(all, "TOPSECRET" in all)
            }
        } finally {
            secret.delete()
        }
    }

    @Test
    fun aFeedLargerThanTheLimitIsReadAsFarAsItGoes() {
        val text = bigFeed(2_000)
        val feed = FeedParser.parse(ByteArrayInputStream(text.toByteArray()), null, maxBytes = 50_000)
        assertTrue(feed.episodes.size in 1 until 2_000)
        assertEquals("Büyük Yayın", feed.title)
    }

    private fun bigFeed(count: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>Büyük Yayın</title>")
        for (i in 0 until count) {
            // 2020-01-01 plus a minute a number, in seconds
            val seconds = 1_577_836_800L + i * 60L
            append("<item><title>Bölüm $i</title><guid>guid-$i</guid><pubDate>$seconds</pubDate>")
            append("<enclosure url=\"https://example.org/$i.mp3\" length=\"1\" type=\"audio/mpeg\"/></item>\n")
        }
        append("</channel></rss>")
    }
}
