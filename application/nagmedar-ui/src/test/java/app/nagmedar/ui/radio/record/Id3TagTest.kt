package app.nagmedar.ui.radio.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1107: the tag at the start of a recorded file is a valid ID3v2.4 tag. */
class Id3TagTest {

    private fun frames(tag: ByteArray): Map<String, String> {
        val size = (0..3).fold(0) { acc, k -> (acc shl 7) or tag[6 + k].toInt() }
        assertEquals(tag.size - 10, size)
        val out = LinkedHashMap<String, String>()
        var i = 10
        while (i < tag.size) {
            val id = String(tag, i, 4, Charsets.ISO_8859_1)
            val length = (0..3).fold(0) { acc, k -> (acc shl 7) or tag[i + 4 + k].toInt() }
            assertEquals("UTF-8 text", 3, tag[i + 10].toInt())
            out[id] = String(tag, i + 11, length - 1, Charsets.UTF_8)
            i += 10 + length
        }
        return out
    }

    @Test
    fun theTagHoldsTitleArtistAlbumDateAndStation() {
        val tag = Id3Tag.build("Sezen Aksu", "Hadi Bakalım", "Gece Cazı", "2026-10-15", "Gece Cazı")
        assertEquals("ID3", String(tag, 0, 3))
        assertEquals(4, tag[3].toInt())
        assertEquals(
            mapOf("TIT2" to "Hadi Bakalım", "TPE1" to "Sezen Aksu", "TALB" to "Gece Cazı", "TDRC" to "2026-10-15", "TRSN" to "Gece Cazı"),
            frames(tag),
        )
    }

    @Test
    fun aFieldThatIsMissingOrBlankIsLeftOut() {
        val tag = Id3Tag.build(null, "Başlık", "  ", "2026-10-15", null)
        assertEquals(mapOf("TIT2" to "Başlık", "TDRC" to "2026-10-15"), frames(tag))
    }

    @Test
    fun aLongTitleHasASyncSafeSize() {
        val tag = Id3Tag.build("A", "x".repeat(300), "r", "2026-10-15", "r")
        assertTrue(tag.size > 300)
        frames(tag)
    }

    @Test
    fun theFramerSkipsTheTagWhole() {
        val tag = Id3Tag.build("A", "B", "C", "2026-10-15", "C")
        assertEquals(tag.size, Id3.skipLength(tag + Streams.mp3(0, 1), 0, tag.size + 417))
    }
}
