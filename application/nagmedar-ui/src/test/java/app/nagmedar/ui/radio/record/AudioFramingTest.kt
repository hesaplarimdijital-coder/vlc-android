package app.nagmedar.ui.radio.record

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** S-1107: MP3 and ADTS frames, and Ogg pages, are found in a stream that starts anywhere, with junk in it. */
class AudioFramingTest {

    private fun units(framer: Framer, stream: ByteArray, chunk: Int = stream.size.coerceAtLeast(1)): List<MediaUnit> {
        val out = ArrayList<MediaUnit>()
        var i = 0
        while (i < stream.size) {
            val n = minOf(chunk, stream.size - i)
            framer.feed(stream, i, n) { out += it }
            i += n
        }
        framer.finish { out += it }
        return out
    }

    private fun ids(list: List<MediaUnit>) = list.map { it.data[4].toInt() and 0xFF }

    @Test
    fun mp3FramesComeOutWholeWithTheirOffsetsAndLengths() {
        val list = units(MpegFramer(), Streams.mp3(0, 20))
        assertEquals(20, list.size)
        assertEquals((1..20).toList(), ids(list))
        assertEquals(0L, list[0].start)
        assertEquals(417L * 7, list[7].start)
        assertTrue(list.all { it.data.size == 417 })
        // 1152 samples at 44.1 kHz
        assertEquals(26_122L, list[0].micros)
    }

    @Test
    fun theStreamMayStartInTheMiddleOfAFrameAndHoldJunkAndTags() {
        val tag = Id3Tag.build("A", "T", "Radyo", "2026-10-15", "Radyo")
        val stream = ByteArray(123) { 0x11 } + Streams.mp3(0, 3) + ByteArray(40) { 0x22 } + tag + Streams.mp3(3, 3)
        val list = units(MpegFramer(), stream)
        assertEquals((1..6).toList(), ids(list))
        assertEquals(123L, list[0].start)
    }

    @Test
    fun aSyncPatternInTheJunkBeforeTheFramesIsNotAFrame() {
        val junk = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(46) { 0x01 }
        val list = units(MpegFramer(), junk + Streams.mp3(0, 5))
        assertEquals((1..5).toList(), ids(list))
        assertEquals(50L, list[0].start)
    }

    @Test
    fun theSameUnitsComeOutHoweverTheReadsCutTheStream() {
        val stream = Streams.mp3(0, 30)
        val whole = ids(units(MpegFramer(), stream))
        val random = Random(3)
        for (chunk in listOf(1, 3, 100, 417, 418, 5_000)) assertEquals("chunk $chunk", whole, ids(units(MpegFramer(), stream, chunk)))
        repeat(20) {
            val framer = MpegFramer()
            val out = ArrayList<MediaUnit>()
            var i = 0
            while (i < stream.size) {
                val n = minOf(1 + random.nextInt(900), stream.size - i)
                framer.feed(stream, i, n) { out += it }
                i += n
            }
            framer.finish { out += it }
            assertEquals(whole, ids(out))
        }
    }

    @Test
    fun aSyncPatternInsideAFrameIsNotAFrame() {
        val frames = Streams.mp3(0, 10)
        // a header-shaped run inside the body of the third frame
        val inside = 417 * 2 + 100
        val fake = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        System.arraycopy(fake, 0, frames, inside, 4)
        val list = units(MpegFramer(), frames)
        assertEquals((1..10).toList(), ids(list))
        assertTrue(list.all { it.data.size == 417 })
    }

    @Test
    fun aFrameCutOffAtTheEndIsDropped() {
        val stream = Streams.mp3(0, 5)
        val list = units(MpegFramer(), stream.copyOf(stream.size - 100))
        assertEquals(4, list.size)
    }

    @Test
    fun aFrameThatEndsTheStreamIsKept() {
        assertEquals(5, units(MpegFramer(), Streams.mp3(0, 5)).size)
    }

    @Test
    fun paddedFramesAreOneByteLonger() {
        // 44.1 kHz frames alternate 417 and 418 bytes at 128 kbit/s when padded
        val padded = Streams.mp3Frame(9).copyOf(418)
        padded[2] = 0x92.toByte()
        val stream = Streams.mp3Frame(1) + padded + Streams.mp3Frame(2)
        val list = units(MpegFramer(), stream)
        assertEquals(listOf(417, 418, 417), list.map { it.data.size })
    }

    @Test
    fun adtsFramesAreFoundByTheirLengthField() {
        val stream = ByteArray(55) { 0x01 } + Streams.adts(0, 12)
        val list = units(AdtsFramer(), stream, chunk = 97)
        assertEquals(12, list.size)
        assertTrue(list.all { it.data.size == 300 })
        assertEquals(55L, list[0].start)
        // 1024 samples at 44.1 kHz
        assertEquals(23_219L, list[0].micros)
        assertEquals((1..12).toList(), list.map { it.data[7].toInt() and 0xFF })
    }

    @Test
    fun theFormatIsWhatTheBytesShowAndTheHeaderOnlyWhenTheyDoNot() {
        assertEquals(AudioFormat.Mp3, AudioFormat.detect("audio/mpeg", Streams.mp3(0, 4), 4 * 417))
        assertEquals(AudioFormat.Aac, AudioFormat.detect("audio/aacp", Streams.adts(0, 4), 4 * 300))
        // a station that says mpeg and sends AAC
        assertEquals(AudioFormat.Aac, AudioFormat.detect("audio/mpeg", Streams.adts(0, 4), 4 * 300))
        assertEquals(AudioFormat.Mp3, AudioFormat.detect(null, Streams.mp3(0, 4), 4 * 417))
        assertEquals(AudioFormat.Mp3, AudioFormat.detect("audio/mpeg", ByteArray(100) { 0x20 }, 100))
        assertNull(AudioFormat.detect("text/html", ByteArray(100) { 0x20 }, 100))
        assertNull(AudioFormat.detect(null, ByteArray(0), 0))
    }

    @Test
    fun oggAndOpusAreToldByTheirFirstPage() {
        val vorbis = Streams.vorbisStream(1, "TITLE=Bir")
        assertEquals(AudioFormat.Ogg, AudioFormat.detect("application/octet-stream", vorbis, vorbis.size))
        val opus = Streams.oggPage("OpusHead".toByteArray() + ByteArray(11), 0, 2)
        assertEquals(AudioFormat.Opus, AudioFormat.detect("audio/ogg", opus, opus.size))
    }

    @Test
    fun theContentTypesOfSegmentedStreamsAreKnown() {
        assertTrue(AudioFormat.isSegmented("application/vnd.apple.mpegurl"))
        assertTrue(AudioFormat.isSegmented("Audio/MP4; codecs=mp4a"))
        assertTrue(AudioFormat.isSegmented("video/MP2T"))
        assertFalse(AudioFormat.isSegmented("audio/mpeg"))
        assertFalse(AudioFormat.isSegmented(null))
        assertEquals(AudioFormat.Mp3, AudioFormat.fromContentType("audio/mpeg; charset=utf-8"))
        assertEquals(AudioFormat.Aac, AudioFormat.fromContentType("AUDIO/AACP"))
        assertNull(AudioFormat.fromContentType("audio/flac"))
    }

    @Test
    fun anId3TagThatIsNotAllThereYetIsWaitedFor() {
        val tag = Id3Tag.build("Artist", "Title", "Radio", "2026-10-15", "Radio")
        val stream = tag + Streams.mp3(0, 3)
        // the first read ends inside the tag
        val framer = MpegFramer()
        val out = ArrayList<MediaUnit>()
        framer.feed(stream, 0, 12) { out += it }
        framer.feed(stream, 12, stream.size - 12) { out += it }
        framer.finish { out += it }
        assertEquals(3, out.size)
        assertEquals(tag.size.toLong(), out[0].start)
    }

    @Test
    fun oggPagesComeOutWithTheirCrcChecked() {
        val stream = Streams.vorbisStream(7, "ARTIST=Sezen Aksu", "TITLE=Hadi Bakalım", audioPages = 5)
        val list = units(OggFramer(), stream, chunk = 61)
        assertEquals(7, list.size)
        assertArrayEquals(stream, list.fold(ByteArray(0)) { acc, u -> acc + u.data })
        assertTrue(list[0].startsTrack)
        assertEquals(TrackTag("Sezen Aksu", "Hadi Bakalım"), list[0].tag)
        assertTrue(list.drop(1).none { it.startsTrack })
        // a page of 1000 samples at 44.1 kHz
        assertEquals(22_675L, list[3].micros)
    }

    @Test
    fun aPageWithABadCrcIsNotAPage() {
        val stream = Streams.vorbisStream(7, "TITLE=A", audioPages = 4)
        val broken = stream.copyOf()
        // damage the body of the last page
        broken[broken.size - 5] = (broken[broken.size - 5] + 1).toByte()
        assertEquals(5, units(OggFramer(), broken).size)
    }

    @Test
    fun aChainedStreamStartsANewTrackWithItsOwnComments() {
        val stream = Streams.vorbisStream(1, "ARTIST=Biri", "TITLE=Bir", audioPages = 10) + Streams.vorbisStream(2, "ARTIST=Öteki", "TITLE=İki", audioPages = 10)
        val list = units(OggFramer(), stream)
        val starts = list.filter { it.startsTrack }
        assertEquals(2, starts.size)
        assertEquals(TrackTag("Biri", "Bir"), starts[0].tag)
        assertEquals(TrackTag("Öteki", "İki"), starts[1].tag)
        assertEquals(24, list.size)
        // the second track begins at the page that begins the second stream
        assertEquals(12, list.indexOf(starts[1]))
    }

    @Test
    fun opusTagsAreReadToo() {
        val stream = Streams.oggPage("OpusHead".toByteArray() + ByteArray(11), 0, 2) +
            Streams.oggPage(Streams.opusTags("TITLE=Yaz", "ARTIST=Biri"), 0, 0, sequence = 1) +
            Streams.oggPage(ByteArray(60), 960, 0, sequence = 2)
        val list = units(OggFramer(), stream)
        assertEquals(3, list.size)
        assertEquals(TrackTag("Biri", "Yaz"), list[0].tag)
        // 960 samples at 48 kHz
        assertEquals(20_000L, list[2].micros)
    }

    @Test
    fun maliciousCommentLengthsDoNotRunAway() {
        val comments = byteArrayOf(3) + "vorbis".toByteArray() + byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F) + ByteArray(20)
        assertNull(VorbisComments.tag(comments))
        val many = byteArrayOf(3) + "vorbis".toByteArray() + byteArrayOf(0, 0, 0, 0) + byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F) + byteArrayOf(5, 0, 0, 0) + "TITLE".toByteArray()
        assertNull(VorbisComments.tag(many))
        assertNull(VorbisComments.tag(ByteArray(5)))
    }
}
