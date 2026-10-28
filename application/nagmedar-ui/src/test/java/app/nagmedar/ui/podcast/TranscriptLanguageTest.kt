package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Podcast transcripts: the language Whisper hears a show in: its own choice, else its feed's, else Whisper's guess. */
class TranscriptLanguageTest {

    @Test
    fun theFeedsLanguageIsHeardWhenTheShowChoseNone() {
        assertEquals("tr", TranscriptLanguage.choose(null, "tr-TR"))
        assertEquals("en", TranscriptLanguage.choose(null, "en_us"))
        assertEquals("de", TranscriptLanguage.choose(null, "de"))
    }

    @Test
    fun whisperGuessesWhenNothingIsSaidOrWhatIsSaidIsUnknown() {
        assertEquals("auto", TranscriptLanguage.choose(null, null))
        assertEquals("auto", TranscriptLanguage.choose(null, "xx-yy"))
        assertEquals("auto", TranscriptLanguage.choose(null, ""))
    }

    @Test
    fun theShowsChoiceComesFirst() {
        assertEquals("en", TranscriptLanguage.choose("en", "tr"))
        // its own guess, whatever the feed says
        assertEquals("auto", TranscriptLanguage.choose("auto", "tr"))
        // a choice Whisper does not know: the feed's
        assertEquals("tr", TranscriptLanguage.choose("xx", "tr"))
    }

    @Test
    fun oldCodesAreWhispers() {
        assertEquals("he", TranscriptLanguage.whisperCode("iw"))
        assertEquals("id", TranscriptLanguage.whisperCode("in-ID"))
        assertEquals("no", TranscriptLanguage.whisperCode("nb-NO"))
        assertEquals("tl", TranscriptLanguage.whisperCode("fil"))
        assertEquals("yue", TranscriptLanguage.whisperCode("yue-HK"))
        assertNull(TranscriptLanguage.whisperCode("tlh"))
    }
}
