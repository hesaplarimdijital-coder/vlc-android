package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1101: the addresses feeds are known by, and the HTML their descriptions carry. */
class FeedTextTest {

    @Test
    fun anAddressTypedOrPasted() {
        assertEquals("https://example.org/feed.xml", FeedUrl.normalize("  https://example.org/feed.xml \n"))
        assertEquals("http://example.org/feed.xml", FeedUrl.normalize("http://example.org/feed.xml"))
        assertEquals("https://example.org/feed.xml", FeedUrl.normalize("<https://example.org/feed.xml>"))
        assertEquals("https://example.org/x?a=1&b=2", FeedUrl.normalize("https://example.org/x?a=1&b=2"))
    }

    @Test
    fun feedSchemesAreHttps() {
        assertEquals("https://example.org/x", FeedUrl.normalize("feed://example.org/x"))
        assertEquals("https://example.org/x", FeedUrl.normalize("itpc://example.org/x"))
        assertEquals("https://example.org/x", FeedUrl.normalize("pcast://example.org/x"))
        assertEquals("https://example.org/x", FeedUrl.normalize("podcast://example.org/x"))
        assertEquals("https://example.org/x", FeedUrl.normalize("feed:https://example.org/x"))
        assertEquals("http://example.org/x", FeedUrl.normalize("feed:http://example.org/x"))
    }

    @Test
    fun noSchemeTriesHttpsThenHttp() {
        assertEquals(listOf("https://example.org/rss", "http://example.org/rss"), FeedUrl.candidates("example.org/rss"))
        assertEquals(listOf("https://example.org/rss"), FeedUrl.candidates("https://example.org/rss"))
    }

    @Test
    fun whatIsNoAddress() {
        listOf("", "   ", "podcast", "hello world", "ftp://example.org/x", "https://", "https:///x", "javascript:alert(1)", ".org/x", "example./x")
            .forEach { assertNull(it, FeedUrl.normalize(it)) }
    }

    @Test
    fun twoSpellingsOfOneFeed() {
        val key = FeedUrl.key("https://Example.org:443/feed/")
        assertEquals(key, FeedUrl.key("http://example.org/feed"))
        assertEquals(key, FeedUrl.key("https://example.org/feed#top"))
        assertEquals(key, FeedUrl.key("HTTPS://EXAMPLE.ORG/feed"))
        assertTrue(key != FeedUrl.key("https://example.org/feed?lang=tr"))
        assertTrue(key != FeedUrl.key("https://example.org/other"))
        assertTrue(FeedUrl.key("https://example.org/Feed") != FeedUrl.key("https://example.org/feed"))
    }

    @Test
    fun resolvingAgainstTheFeedsAddress() {
        assertEquals("https://example.org/c.mp3", FeedUrl.resolve("https://example.org/a/b.xml", "../c.mp3"))
        assertEquals("https://example.org/x%20y.mp3", FeedUrl.resolve("https://example.org/a", "/x y.mp3"))
        assertEquals("https://cdn.example.org/z.mp3", FeedUrl.resolve("https://example.org/a", "https://cdn.example.org/z.mp3"))
        assertNull(FeedUrl.resolve("https://example.org/a", "javascript:alert(1)"))
        assertNull(FeedUrl.resolve("https://example.org/a", "mailto:a@example.org"))
        assertNull(FeedUrl.resolve(null, "/relative.mp3"))
        assertNull(FeedUrl.resolve("https://example.org/a", "   "))
    }

    @Test
    fun htmlAsText() {
        assertEquals("Her hafta gece yarısı.\nMüzik & sohbet.", HtmlText.plain("<p>Her hafta <b>gece</b> yarısı.</p><p>Müzik &amp; sohbet.</p>"))
        assertEquals("bir\niki", HtmlText.plain("bir<br>iki"))
        assertEquals("bir\niki", HtmlText.plain("bir<br />iki"))
        assertEquals("• Neva Kâr\n\n• Segâh", HtmlText.plain("<ul><li>Neva Kâr</li><li>Segâh</li></ul>"))
        assertEquals("tırnak \"ve\" ü ş … ©", HtmlText.plain("tırnak &quot;ve&quot; &#252; &#x15F; &hellip; &copy;"))
        assertEquals("a < b", HtmlText.plain("a &lt; b"))
        assertEquals("sonra", HtmlText.plain("<script>alert('x')</script><style>p{}</style><!-- gizli -->sonra"))
        // an entity nobody knows stays as written
        assertEquals("a &bilinmeyen; b", HtmlText.plain("a &bilinmeyen; b"))
        // a lone ampersand and a bare "<" are not markup
        assertEquals("Tom & Jerry 3 < 5", HtmlText.plain("Tom & Jerry 3 < 5"))
        assertEquals("a b", HtmlText.plain("a  ​  b"))
    }

    @Test
    fun nothingLeftIsNull() {
        assertNull(HtmlText.plain(null))
        assertNull(HtmlText.plain("   "))
        assertNull(HtmlText.plain("<p> </p><br>"))
        assertNull(HtmlText.line("<b></b>"))
    }

    @Test
    fun aLongTextIsCut() {
        val cut = HtmlText.plain("a".repeat(5_000), limit = 100)!!
        assertEquals(101, cut.length)
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun aLineIsOneLine() {
        assertEquals("Bir iki", HtmlText.line("<p>Bir</p><p>iki</p>"))
        assertEquals("Kötü Yayın", HtmlText.line("  Kötü   Yayın \n"))
    }

    @Test
    fun entitiesAreDecodedOnce() {
        assertEquals("&lt;", HtmlText.plain("&amp;lt;"))
    }

    @Test
    fun scriptsStylesAndCommentsAreDropped() {
        assertEquals("a b", HtmlText.plain("a <script>alert('x')</script> b"))
        assertEquals("a b", HtmlText.plain("a <STYLE type=\"text/css\">p { color: red }</STYLE > b"))
        assertEquals("a b", HtmlText.plain("a <!-- hidden <b>bold</b> --> b"))
        assertEquals("a b", HtmlText.plain("a < script >x</ script> b"))
        // not a script, only a word that starts like one
        assertEquals("a x b", HtmlText.plain("a <scripture>x</scripture> b"))
    }

    @Test
    fun anUnclosedScriptOrCommentIsLeftAlone() {
        assertEquals("a alert(1) b", HtmlText.plain("a <script>alert(1) b"))
        assertEquals("a <!-- b", HtmlText.plain("a <!-- b"))
    }

    @Test
    fun markupThatIsAllOpenersIsReadInLinearTime() {
        // 200,000 characters of openers with nothing to close them: each of these took minutes as a lazy regular expression
        listOf("<script ", "<style ", "<!--", "<li", "<a<a", "< < ").forEach { piece ->
            val text = piece.repeat(200_000 / piece.length)
            val started = System.nanoTime()
            HtmlText.plain(text)
            val ms = (System.nanoTime() - started) / 1_000_000
            assertTrue("$piece took $ms ms", ms < 3_000)
        }
    }
}
