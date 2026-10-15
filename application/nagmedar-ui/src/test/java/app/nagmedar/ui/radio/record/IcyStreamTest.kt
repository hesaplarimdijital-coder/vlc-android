package app.nagmedar.ui.radio.record

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random

/** S-1107: the ICY metadata is taken out of the audio at the byte it is announced for, however the reads cut the stream. */
class IcyStreamTest {

    private class Collected : IcyDemuxer.Listener {
        val audio = ByteArrayOutputStream()
        val blocks = ArrayList<Pair<Long, String>>()

        override fun audio(data: ByteArray, offset: Int, length: Int) = audio.write(data, offset, length)

        override fun metadata(block: ByteArray, length: Int, audioOffset: Long) {
            blocks += audioOffset to StreamTitles.decode(block, length)
        }
    }

    private fun feed(stream: ByteArray, metaInt: Int, chunk: Int): Collected {
        val out = Collected()
        val demux = IcyDemuxer(metaInt, out)
        var i = 0
        while (i < stream.size) {
            val n = minOf(chunk, stream.size - i)
            demux.feed(stream, i, n)
            i += n
        }
        return out
    }

    private val audio = ByteArray(10_000) { (it % 251).toByte() }

    @Test
    fun withoutMetadataEverythingIsAudio() {
        val out = feed(audio, 0, 777)
        assertArrayEquals(audio, out.audio.toByteArray())
        assertEquals(0, out.blocks.size)
    }

    @Test
    fun blocksComeOutAtTheirBoundaries() {
        val stream = Streams.icy(audio, 4_096) { if (it == 0) "Sezen Aksu - Hadi Bakalım" else null }
        val out = feed(stream, 4_096, stream.size)
        assertArrayEquals(audio, out.audio.toByteArray())
        // one announced title at 4096 audio bytes; the other block is empty ("no change") and says nothing
        assertEquals(1, out.blocks.size)
        assertEquals(4_096L, out.blocks[0].first)
        assertEquals("StreamTitle='Sezen Aksu - Hadi Bakalım';", out.blocks[0].second)
    }

    @Test
    fun aBlockSplitOverReadsIsTheSameAsOneRead() {
        val stream = Streams.icy(audio, 1_000) { "Artist $it - Title $it" }
        val whole = feed(stream, 1_000, stream.size)
        for (chunk in listOf(1, 2, 7, 16, 17, 333, 1_001)) {
            val split = feed(stream, 1_000, chunk)
            assertArrayEquals("chunk $chunk", audio, split.audio.toByteArray())
            assertEquals("chunk $chunk", whole.blocks, split.blocks)
        }
        assertEquals(10, whole.blocks.size)
    }

    @Test
    fun randomReadSizesChangeNothing() {
        val random = Random(7)
        val stream = Streams.icy(audio, 333) { if (it % 3 == 0) "T$it" else null }
        val expected = feed(stream, 333, stream.size)
        repeat(30) {
            val out = Collected()
            val demux = IcyDemuxer(333, out)
            var i = 0
            while (i < stream.size) {
                val n = minOf(1 + random.nextInt(400), stream.size - i)
                demux.feed(stream, i, n)
                i += n
            }
            assertArrayEquals(audio, out.audio.toByteArray())
            assertEquals(expected.blocks, out.blocks)
        }
    }

    @Test
    fun theLargestBlockAFieldCanAnnounceIsRead() {
        val big = "x".repeat(4_000)
        val stream = Streams.icy(audio.copyOf(200), 100) { if (it == 0) big else null }
        val out = feed(stream, 100, 64)
        assertArrayEquals(audio.copyOf(200), out.audio.toByteArray())
        assertEquals("StreamTitle='$big';", out.blocks.single().second)
    }

    @Test
    fun aLengthByteOf255IsNoMoreThan4080BytesWhateverTheServerMeans() {
        // 255 sixteens followed by less than that: the block is cut short by the end of the stream, nothing runs away
        val stream = ByteArray(10) { 1 } + byteArrayOf(0xFF.toByte()) + ByteArray(100) { 0x41 }
        val out = feed(stream, 10, 30)
        assertEquals(10, out.audio.size())
        assertEquals(0, out.blocks.size)
    }

    @Test
    fun aMetaIntOfOneTakesBlocksBetweenEveryByte() {
        val small = ByteArray(5) { (it + 1).toByte() }
        val out = feed(Streams.icy(small, 1) { "t$it" }, 1, 3)
        assertArrayEquals(small, out.audio.toByteArray())
        assertEquals(5, out.blocks.size)
    }

    @Test
    fun theHeaderIsOnlyTakenWhenItIsAPositiveNumber() {
        assertEquals(16000, IcyDemuxer.metaInt("16000"))
        assertEquals(16000, IcyDemuxer.metaInt(" 16000 "))
        assertEquals(0, IcyDemuxer.metaInt(null))
        assertEquals(0, IcyDemuxer.metaInt("abc"))
        assertEquals(0, IcyDemuxer.metaInt("-5"))
        assertEquals(0, IcyDemuxer.metaInt("0"))
        assertEquals(0, IcyDemuxer.metaInt("99999999999999999999"))
    }

    @Test
    fun theTitleIsTheValueOfStreamTitle() {
        assertEquals("Artist - Title", StreamTitles.title("StreamTitle='Artist - Title';"))
        assertEquals("Artist - Title", StreamTitles.title("StreamTitle='Artist - Title';StreamUrl='http://x';"))
        assertEquals("", StreamTitles.title("StreamTitle='';StreamUrl='';"))
        assertNull(StreamTitles.title("StreamUrl='http://x';"))
        assertNull(StreamTitles.title(""))
    }

    @Test
    fun aQuoteInATitleDoesNotEndIt() {
        assertEquals("Journey - Don't Stop Believin'", StreamTitles.title("StreamTitle='Journey - Don't Stop Believin'';StreamUrl='';"))
        assertEquals("Rock 'n' Roll", StreamTitles.title("StreamTitle='Rock 'n' Roll';"))
    }

    @Test
    fun aBlockThatIsNotClosedGivesWhatIsThere() {
        assertEquals("Artist - Title", StreamTitles.title("StreamTitle='Artist - Title'"))
        assertEquals("Artist - Title", StreamTitles.title("StreamTitle='Artist - Title"))
    }

    @Test
    fun utf8IsReadAsUtf8AndOlderTurkishAsWindows1254() {
        // the block without its length byte
        val utf8 = Streams.icyBlock("Şebnem Ferah - Çile", Charsets.UTF_8).drop(1).toByteArray()
        assertEquals("StreamTitle='Şebnem Ferah - Çile';", StreamTitles.decode(utf8, utf8.size))
        val legacy = Streams.icyBlock("Şebnem Ferah - Çile ığİ", charset("windows-1254")).drop(1).toByteArray()
        assertEquals("StreamTitle='Şebnem Ferah - Çile ığİ';", StreamTitles.decode(legacy, legacy.size))
    }

    @Test
    fun bytesThatAreNoTextDoNotBreakTheDecoding() {
        val junk = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x80.toByte(), 'a'.code.toByte(), 0, 0)
        assertEquals(4, StreamTitles.decode(junk, junk.size).length)
        assertEquals("", StreamTitles.decode(ByteArray(16), 16))
        assertEquals("", StreamTitles.decode(ByteArray(0), 99))
    }
}
