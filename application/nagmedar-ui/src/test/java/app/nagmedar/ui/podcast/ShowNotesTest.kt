package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-205: show notes: the HTML of a feed read into a few safe blocks, and nothing else of it kept. */
class ShowNotesTest {

    private fun runs(block: NoteBlock) = when (block) {
        is NoteBlock.Paragraph -> block.runs
        is NoteBlock.Heading -> block.runs
        is NoteBlock.Item -> block.runs
        is NoteBlock.Quote -> block.runs
        is NoteBlock.Picture -> emptyList()
    }

    private fun text(blocks: List<NoteBlock>) = blocks.joinToString("|") { runs(it).joinToString("") { run -> run.text } }

    @Test
    fun paragraphsAndBreaksAreKept() {
        val blocks = ShowNotes.fromHtml("<p>Birinci  paragraf.</p>\n<p>İkinci<br>satır.</p><div>Üçüncü</div>")
        assertEquals("Birinci paragraf.|İkinci\nsatır.|Üçüncü", text(blocks))
    }

    @Test
    fun headingsListsAndQuotesAreKept() {
        val blocks = ShowNotes.fromHtml("<h2>Konular</h2><ul><li>Neva</li><li>Segâh<ul><li>Alt</li></ul></li></ul><ol><li>bir</li><li>iki</li></ol><blockquote>Söz</blockquote>")
        assertEquals(NoteBlock.Heading(2, listOf(NoteRun("Konular"))), blocks[0])
        assertEquals(NoteBlock.Item("•", 0, listOf(NoteRun("Neva"))), blocks[1])
        assertEquals(NoteBlock.Item("•", 0, listOf(NoteRun("Segâh"))), blocks[2])
        assertEquals(NoteBlock.Item("•", 1, listOf(NoteRun("Alt"))), blocks[3])
        assertEquals(NoteBlock.Item("1.", 0, listOf(NoteRun("bir"))), blocks[4])
        assertEquals(NoteBlock.Item("2.", 0, listOf(NoteRun("iki"))), blocks[5])
        assertEquals(NoteBlock.Quote(listOf(NoteRun("Söz"))), blocks[6])
    }

    @Test
    fun stylesAreKept() {
        val runs = runs(ShowNotes.fromHtml("<p>a <b>kalın</b> <i>eğik</i> <strong><em>ikisi</em></strong> <code>kod</code></p>").single())
        assertEquals(listOf("a ", "kalın", " ", "eğik", " ", "ikisi", " ", "kod"), runs.map { it.text })
        assertTrue(runs[1].bold && !runs[1].italic)
        assertTrue(runs[3].italic && !runs[3].bold)
        assertTrue(runs[5].bold && runs[5].italic)
        assertTrue(runs[7].code)
    }

    @Test
    fun scriptsStylesAndFramesGoWithTheirContent() {
        val html = "<p>önce</p><script>alert('x')</script><style>p{color:red}</style><iframe src='https://evil.example/'>içerik</iframe><object data='x'>nesne</object><form action='/x'><input name=a>forma</form><p>sonra</p>"
        assertEquals("önce|sonra", text(ShowNotes.fromHtml(html)))
    }

    @Test
    fun anUnclosedScriptDropsTheRest() {
        assertEquals("önce", text(ShowNotes.fromHtml("<p>önce</p><script>alert(1)<p>sonra</p>")))
    }

    @Test
    fun otherTagsGoButTheirTextStays() {
        assertEquals("kalın metinbir iki", text(ShowNotes.fromHtml("<font color='red'><span style='x'>kalın metin</span></font><marquee>bir</marquee> <blink>iki</blink>")))
    }

    @Test
    fun onlyHttpAndMailtoLinksSurvive() {
        val html = "<p><a href='https://example.org/x?a=1&amp;b=2'>iyi</a> <a href='javascript:alert(1)'>kötü</a> <a href='data:text/html,x'>veri</a> <a href='file:///sdcard'>dosya</a> <a href='mailto:ben@example.org'>posta</a> <a href='intent://x#Intent;end'>niyet</a></p>"
        val runs = runs(ShowNotes.fromHtml(html).single())
        assertEquals(listOf("iyi", "posta"), runs.filter { it.link != null }.map { it.text })
        assertEquals("https://example.org/x?a=1&b=2", runs.first { it.text == "iyi" }.link)
        assertEquals("mailto:ben@example.org", runs.first { it.text == "posta" }.link)
        // the others stay as plain text
        assertTrue(runs.any { "kötü" in it.text && it.link == null })
        assertEquals(listOf("https://example.org/x?a=1&b=2", "mailto:ben@example.org"), ShowNotes.links(listOf(NoteBlock.Paragraph(runs))))
    }

    @Test
    fun relativeLinksAreMadeAbsoluteAgainstTheFeed() {
        val runs = runs(ShowNotes.fromHtml("<p><a href='/bolum/4'>dört</a></p>", base = "https://example.org/feed.xml").single())
        assertEquals("https://example.org/bolum/4", runs.single().link)
    }

    @Test
    fun anAddressWrittenOutBecomesALink() {
        val runs = runs(ShowNotes.fromHtml("<p>Bkz. https://example.org/destek, ve (http://example.org/b).</p>").single())
        assertEquals(listOf("Bkz. ", "https://example.org/destek", ", ve (", "http://example.org/b", ")."), runs.map { it.text })
        assertEquals("https://example.org/destek", runs[1].link)
        assertEquals("http://example.org/b", runs[3].link)
    }

    @Test
    fun picturesNeedAnHttpAddress() {
        val blocks = ShowNotes.fromHtml("<p>x</p><img src='https://cdn.example.org/a.jpg' alt='Kapak  resmi'><img src='data:image/png;base64,AAAA'><img src='javascript:1'>")
        assertEquals(listOf(NoteBlock.Paragraph(listOf(NoteRun("x"))), NoteBlock.Picture("https://cdn.example.org/a.jpg", "Kapak resmi")), blocks)
    }

    @Test
    fun entitiesAreDecodedOnceAndNeverRunAsMarkup() {
        val blocks = ShowNotes.fromHtml("<p>1 &lt; 2 &amp;&amp; &lt;script&gt;alert(1)&lt;/script&gt; &#39;t&#x131;rnak&#39;</p>")
        assertEquals("1 < 2 && <script>alert(1)</script> 'tırnak'", text(blocks))
    }

    @Test
    fun commentsAndDeclarationsAreDropped() {
        assertEquals("a|b", text(ShowNotes.fromHtml("<!DOCTYPE html><p>a</p><!-- <p>gizli</p> --><p>b</p><?php echo 1 ?>")))
    }

    @Test
    fun strayAngleBracketsAreText() {
        assertEquals("3 < 4 ve 5 > 2 <3", text(ShowNotes.fromHtml("<p>3 < 4 ve 5 > 2 <3</p>")))
    }

    @Test
    fun atagThatNeverEndsIsText() {
        assertEquals("a <b href=\"x", text(ShowNotes.fromHtml("<p>a <b href=\"x</p>")))
    }

    @Test
    fun preKeepsItsLines() {
        val blocks = ShowNotes.fromHtml("<pre>bir\n  iki\n</pre><p>sonra</p>")
        assertEquals("bir\n  iki|sonra", text(blocks))
        assertTrue(runs(blocks[0]).single().code)
    }

    @Test
    fun aPlainNoteBecomesParagraphsWithLinks() {
        val blocks = ShowNotes.fromPlain("Birinci satır\nikinci satır\n\nBkz. https://example.org/a.\n\n\n")
        assertEquals(2, blocks.size)
        assertEquals("Birinci satır\nikinci satır", text(blocks.take(1)))
        assertEquals("https://example.org/a", runs(blocks[1]).first { it.link != null }.link)
    }

    @Test
    fun theHtmlIsUsedWhenThereIsSomeElseThePlainText() {
        assertEquals("html", text(ShowNotes.of("<p>html</p>", "düz")))
        assertEquals("düz", text(ShowNotes.of(null, "düz")))
        assertEquals("düz", text(ShowNotes.of("   ", "düz")))
        assertEquals("düz", text(ShowNotes.of("<script>x</script>", "düz")))
        assertTrue(ShowNotes.of(null, null).isEmpty())
    }

    @Test
    fun aHugeNoteIsCut() {
        val html = (1..5_000).joinToString("") { "<p>satır $it</p>" }
        assertEquals(ShowNotes.MAX_BLOCKS, ShowNotes.fromHtml(html).size)
    }

    @Test
    fun deeplyNestedMarkupDoesNotBreak() {
        val html = "<div>".repeat(20_000) + "içerik" + "</div>".repeat(20_000) + "<ul>".repeat(100) + "<li>x"
        assertFalse(ShowNotes.fromHtml(html).isEmpty())
    }
}
