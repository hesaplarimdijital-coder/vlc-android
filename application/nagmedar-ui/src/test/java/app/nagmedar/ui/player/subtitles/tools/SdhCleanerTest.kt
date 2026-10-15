package app.nagmedar.ui.player.subtitles.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-708: the cleaning of subtitles for the hard of hearing, on lines as releases write them, in Turkish and English. */
class SdhCleanerTest {

    private val all = SdhOptions()

    private fun clean(text: String, options: SdhOptions = all) = SdhCleaner.cleanText(text, options)

    @Test
    fun soundDescriptionsInBracketsGo() {
        assertNull(clean("[kapı çarpar]"))
        assertNull(clean("[door slams]"))
        assertEquals("Kim o?", clean("[kapı çarpar] Kim o?"))
        assertEquals("Who is it?", clean("Who is it? [knocking]"))
        // a description over two lines
        assertNull(clean("[kapı sert bir\nşekilde çarpar]"))
        assertEquals("Tamam.", clean("[telefon çalar]\nTamam."))
    }

    @Test
    fun parenthesesGoWhenTheyAreDescriptions() {
        assertNull(clean("(MÜZİK)"))
        assertNull(clean("(SIGHS)"))
        assertEquals("Evet.", clean("(güler) Evet."))
        assertEquals("Evet.", clean("Evet. (güler)"))
        assertEquals("Evet.", clean("Evet (güler)."))
        // an aside in the middle of a sentence is said
        assertEquals("Ona (yani Ali'ye) söyledim.", clean("Ona (yani Ali'ye) söyledim."))
        assertEquals("1999 (ya da 2000) idi.", clean("1999 (ya da 2000) idi."))
        // off
        assertEquals("(güler) Evet.", clean("(güler) Evet.", all.copy(parentheses = false)))
    }

    @Test
    fun speakerLabelsGo() {
        assertEquals("Kim o?", clean("ADAM: Kim o?"))
        assertEquals("Where were you?", clean("JOHN: Where were you?"))
        assertEquals("Hello.", clean("DR. SMITH: Hello."))
        assertEquals("Gel.", clean("ŞEREF BEY: Gel."))
        assertEquals("Hello.", clean("ADAM (O.S.): Hello."))
        assertNull(clean("ADAM:"))
        // the dash of a dialogue stays while both speak; the lone line loses it
        assertEquals("- Kim o?\n- Benim.", clean("- ADAM: Kim o?\n- EVE: Benim."))
        assertEquals("Benim.", clean("- [kapı çarpar]\n- Benim."))
        // what is not a label stays
        assertEquals("Saat 12:30'da.", clean("Saat 12:30'da.", all.copy(speakerLabels = true)))
        assertEquals("Adam: Kim o?", clean("Adam: Kim o?"))
        assertEquals("He said: no.", clean("He said: no."))
        assertEquals("ADAM: Kim o?", clean("ADAM: Kim o?", all.copy(speakerLabels = false)))
    }

    @Test
    fun namesWrittenAsNamesGoOnlyWhenAsked() {
        val named = all.copy(namedSpeakers = true)
        assertEquals("Kim o?", clean("Adam: Kim o?", named))
        assertEquals("Merhaba.", clean("Ayşe Hanım: Merhaba.", named))
        assertEquals("Who?", clean("Mr. Smith: Who?", named))
    }

    @Test
    fun linesOfMusicGoWhenAsked() {
        assertEquals("♪ la la ♪", clean("♪ la la ♪"))
        assertNull(clean("♪ la la ♪", all.copy(music = true)))
        assertEquals("Tamam.", clean("♪ şarkı ♪\nTamam.", all.copy(music = true)))
        assertNull(clean("♪♪", all.copy(music = true)))
    }

    @Test
    fun fontTagsGoAndItalicsStay() {
        assertEquals("<i>Burada kimse yok.</i>", clean("<font color=\"#ffff00\"><i>Burada kimse yok.</i></font>"))
        assertEquals("Hello", clean("<FONT face='Arial' size=\"18\">Hello</FONT>"))
        assertNull(clean("<i>[music playing]</i>"))
        assertEquals("<i>Hello.</i>", clean("<i>[sighs] Hello.</i>"))
    }

    @Test
    fun assOverrideTagsAreNotText() {
        assertEquals("{\\an8}Kim o?", clean("{\\an8}ADAM: Kim o?"))
        assertNull(clean("{\\an8}[kapı çarpar]"))
        assertEquals("{\\an8}Tamam.", clean("{\\an8}(MÜZİK)\nTamam."))
    }

    @Test
    fun assOverrideBlocksWithBracketsAndParenthesesAreLeftAlone() {
        assertEquals("{\\clip(0,0,100,100)}Merhaba", clean("{\\clip(0,0,100,100)}[kapı çarpar] Merhaba"))
        assertEquals("{\\clip(0,0,100,100)}Merhaba", clean("{\\clip(0,0,100,100)}Merhaba"))
        assertEquals("{\\pos(960,540)\\fad(200,200)}Tamam.", clean("{\\pos(960,540)\\fad(200,200)}(güler) Tamam."))
        assertEquals("{\\t(0,500,\\fs60)}Kim o?", clean("{\\t(0,500,\\fs60)}ADAM: Kim o?"))
    }

    @Test
    fun aTextNothingTouchesStaysAsItWas() {
        val text = "Merhaba   dünya ,  nasılsın ?\n  - Iyi"
        assertEquals(text, clean(text))
    }

    @Test
    fun theResultSaysWhatChanged() {
        val cues = listOf(
            TimedCue(1, 1_000, 2_000, "[kapı çarpar]"),
            TimedCue(2, 3_000, 4_000, "ADAM: Kim o?"),
            TimedCue(3, 5_000, 6_000, "Kimse yok."),
            TimedCue(4, 7_000, 8_000, "(MÜZİK)"),
        )
        val result = SdhCleaner.clean(cues, all)
        assertEquals(4, result.before)
        assertEquals(2, result.after)
        assertEquals(2, result.removed)
        assertEquals(1, result.edited)
        assertEquals(listOf("Kim o?", "Kimse yok."), result.cues.map { it.text })
        assertEquals(listOf(1, 2, 4), result.changes.map { it.number })
        assertEquals(CueChange(2, 2, 3_000, "ADAM: Kim o?", "Kim o?"), result.changes[1])
        assertNull(result.changes[0].after)
    }

    @Test
    fun aWholeFileOfEachLanguageIsCleaned() {
        val turkish = SubtitleCodec.parse(
            "a.srt",
            "1\n00:00:01,000 --> 00:00:02,000\n[kapı çarpar]\n\n" +
                "2\n00:00:03,000 --> 00:00:04,000\nADAM: Geç kaldın.\n\n" +
                "3\n00:00:05,000 --> 00:00:06,000\n- (MÜZİK DEVAM EDER)\n- <i>Burada kimse yok.</i>\n\n" +
                "4\n00:00:07,000 --> 00:00:08,000\nEVE (telefonda): Geliyorum.\n\n",
        )!!
        val cleaned = SdhCleaner.clean(turkish.cues, all)
        assertEquals(listOf("Geç kaldın.", "<i>Burada kimse yok.</i>", "Geliyorum."), cleaned.cues.map { it.text })
        val english = SubtitleCodec.parse(
            "a.srt",
            "1\n00:00:01,000 --> 00:00:02,000\n[DOOR SLAMS]\n\n" +
                "2\n00:00:03,000 --> 00:00:04,000\nMARY: You're late.\n- [chuckles] I know.\n\n" +
                "3\n00:00:05,000 --> 00:00:06,000\n♪ Sweet home Alabama ♪\n\n",
        )!!
        val result = SdhCleaner.clean(english.cues, all.copy(music = true))
        assertEquals(listOf("You're late.\n- I know."), result.cues.map { it.text })
    }
}
