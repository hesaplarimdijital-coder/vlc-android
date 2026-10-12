package app.nagmedar.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** S-1003: the engine's lyrics JSON (nagaudio's own prints, as test-audio.sh checks them) read. */
@RunWith(AndroidJUnit4::class)
class TagLyricsTest {

    @Test
    fun plainAndSynced() {
        val lyrics = TagLyrics.parse("""{"plain":"Düz söz\nikinci","synced":[[0,"Gece"],[1200,"Yolcusu"],[1800,"şarkı"]]}""")!!
        assertEquals("Düz söz\nikinci", lyrics.plain)
        assertEquals(listOf(TagLyrics.Line(0, "Gece"), TagLyrics.Line(1200, "Yolcusu"), TagLyrics.Line(1800, "şarkı")), lyrics.synced)
    }

    @Test
    fun plainThatIsLrc() {
        val lyrics = TagLyrics.parse("""{"plain":"[00:01.00]Birinci satır\n[00:01.50]İkinci \"satır\""}""")!!
        assertEquals("[00:01.00]Birinci satır\n[00:01.50]İkinci \"satır\"", lyrics.plain)
        assertEquals(emptyList<TagLyrics.Line>(), lyrics.synced)
    }

    @Test
    fun noneIsNull() {
        assertNull(TagLyrics.parse(null))
        assertNull(TagLyrics.parse("{}"))
        assertNull(TagLyrics.parse("""{"plain":"  \n"}"""))
        assertNull(TagLyrics.parse("""{"synced":[]}"""))
        assertNull(TagLyrics.parse("not json"))
    }
}
