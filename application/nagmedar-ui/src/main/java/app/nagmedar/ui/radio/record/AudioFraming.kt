package app.nagmedar.ui.radio.record

import java.util.Locale

/** S-1107: the containers a station's stream is recorded in, each as the station sends it. */
enum class AudioFormat(val extension: String, val mime: String, val label: String) {
    Mp3("mp3", "audio/mpeg", "MP3"),
    Aac("aac", "audio/aac", "AAC"),
    Ogg("ogg", "audio/ogg", "Ogg"),
    Opus("opus", "audio/ogg", "Opus"),
    ;

    /** Whether the stream is cut between Ogg pages (a chained stream starts a new track) rather than between frames. */
    val isOgg: Boolean get() = this == Ogg || this == Opus

    companion object {
        /** The format a `Content-Type` names; null for one that names none of these. */
        fun fromContentType(type: String?): AudioFormat? = when (type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
            "audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/mpeg3", "audio/x-mp3" -> Mp3
            "audio/aac", "audio/aacp", "audio/x-aac", "audio/aac+" -> Aac
            "audio/ogg", "application/ogg", "audio/x-ogg", "audio/vorbis" -> Ogg
            "audio/opus" -> Opus
            else -> null
        }

        /** Whether the `Content-Type` is a playlist or a segmented stream (HLS, MPEG-TS, MP4) that is not recorded as a plain stream. */
        fun isSegmented(type: String?): Boolean {
            val t = type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return false
            return t in SEGMENTED
        }

        private val SEGMENTED = setOf(
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl",
            "video/mp2t", "audio/mp4", "video/mp4", "audio/x-m4a",
        )

        /**
         * The format of a stream whose first [length] bytes are [head] and whose `Content-Type` is
         * [contentType]: what the bytes show (a chain of frames, "OggS") wins over a header that
         * stations get wrong; the header decides when the bytes do not.
         */
        fun detect(contentType: String?, head: ByteArray, length: Int): AudioFormat? {
            val end = length.coerceIn(0, head.size)
            if (end >= 4 && head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() && head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte()) {
                return if (contains(head, end, "OpusHead")) Opus else Ogg
            }
            val sniffed = sniffFrames(head, end)
            return sniffed ?: fromContentType(contentType)
        }

        private fun contains(data: ByteArray, end: Int, word: String): Boolean {
            val w = word.toByteArray(Charsets.ISO_8859_1)
            val last = minOf(end, 512) - w.size
            for (i in 0..last) {
                var same = true
                for (j in w.indices) if (data[i + j] != w[j]) { same = false; break }
                if (same) return true
            }
            return false
        }

        /** The frame format whose first header in [head] is followed by another of its own, else null. */
        private fun sniffFrames(head: ByteArray, end: Int): AudioFormat? {
            var i = Id3.skipLength(head, 0, end).coerceAtLeast(0)
            while (i + 4 <= end) {
                if (head[i] == 0xFF.toByte()) {
                    for ((format, parse) in listOf(Mp3 to MpegAudio::header, Aac to Adts::header)) {
                        val h = parse(head, i, end) ?: continue
                        val next = i + h.length
                        if (next + 7 <= end && parse(head, next, end) != null) return format
                    }
                }
                i++
            }
            return null
        }
    }
}

/**
 * S-1107: a piece the stream is cut between: an MP3 or ADTS frame, or an Ogg page. [start] is the
 * audio byte (metadata not counted) it begins at, [micros] how long it plays. An Ogg page that
 * begins a new logical stream carries the [tag] its comments name and has [startsTrack] set.
 */
class MediaUnit(
    val data: ByteArray,
    val start: Long,
    val micros: Long,
    val startsTrack: Boolean = false,
    val tag: TrackTag? = null,
)

/** A frame header's length in bytes and how long the frame plays. */
internal class FrameHeader(val length: Int, val micros: Long)

/**
 * S-1107: cuts an audio stream into [MediaUnit]s, throwing away what is no unit (junk between
 * frames, ID3 tags, a frame cut off by the stream's start) so that a file made of whole units is
 * clean. A header is trusted when the next unit's header follows it, which a chance 0xFF 0xFB in
 * the audio does not satisfy. Units reach [emit] in order.
 */
abstract class Framer {
    private var buffer = ByteArray(INITIAL)
    private var filled = 0

    /** The audio byte [buffer]'s first byte is. */
    private var base = 0L

    /** Takes [length] bytes of audio from [offset] of [data]; whole units go to [emit]. */
    fun feed(data: ByteArray, offset: Int, length: Int, emit: (MediaUnit) -> Unit) {
        if (length <= 0) return
        if (filled + length > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, filled + length))
        System.arraycopy(data, offset, buffer, filled, length)
        filled += length
        scan(false, emit)
    }

    /** The stream ended: what is left that makes a whole unit goes to [emit]. */
    fun finish(emit: (MediaUnit) -> Unit) {
        scan(true, emit)
        filled = 0
    }

    private fun scan(final: Boolean, emit: (MediaUnit) -> Unit) {
        var i = 0
        val sink = UnitSink(emit)
        while (i < filled) {
            val used = step(buffer, i, filled, final, base + i, sink)
            if (used <= 0) break
            i += used
        }
        if (final) release(sink)
        if (i > 0) {
            System.arraycopy(buffer, i, buffer, 0, filled - i)
            filled -= i
            base += i
        }
    }

    /**
     * Looks at [data] from [from] ([end] bytes are there; [start] is the audio byte [from] is) and
     * returns how many bytes it used up: a unit's, junk's, or 0 to wait for more bytes. A [final]
     * call must use up what it can.
     */
    protected abstract fun step(data: ByteArray, from: Int, end: Int, final: Boolean, start: Long, sink: UnitSink): Int

    /** The stream ended: units a framer kept back are handed over. */
    protected open fun release(sink: UnitSink) = Unit

    private companion object {
        const val INITIAL = 16 * 1024
    }
}

/** What a [Framer.step] hands units to: each is a copy of the bytes it covers. */
class UnitSink(private val out: (MediaUnit) -> Unit) {
    fun unit(data: ByteArray, from: Int, length: Int, start: Long, micros: Long, startsTrack: Boolean = false, tag: TrackTag? = null) {
        out(MediaUnit(data.copyOfRange(from, from + length), start, micros, startsTrack, tag))
    }
}

/** Frames with a four or seven byte header that says how long the frame is: MP3 and ADTS. */
abstract class FrameFramer internal constructor(private val header: (ByteArray, Int, Int) -> FrameHeader?, private val headerBytes: Int) : Framer() {

    /** The audio byte the next frame begins at when the last one was followed by another: such a frame is trusted without a look at the one after it. */
    private var expected = -1L

    override fun step(data: ByteArray, from: Int, end: Int, final: Boolean, start: Long, sink: UnitSink): Int {
        val tag = Id3.skipLength(data, from, end)
        if (tag != 0) {
            // an ID3 tag between frames: dropped, whole
            if (tag > 0) return tag.takeIf { from + it <= end } ?: if (final) end - from else 0
            return if (final) end - from else 0
        }
        if (end - from < headerBytes && !final) return 0
        val h = header(data, from, end)
        if (h == null) {
            // junk: up to the next possible sync byte
            var j = from + 1
            while (j < end && data[j] != 0xFF.toByte()) j++
            return j - from
        }
        val next = from + h.length
        if (next > end) return if (final) end - from else 0
        if (start != expected) {
            // not in step with the last frame: a header is trusted when another follows it
            if (end - next >= headerBytes) {
                if (header(data, next, end) == null && Id3.skipLength(data, next, end) == 0) return 1
            } else if (!final) {
                return 0
            }
        }
        sink.unit(data, from, h.length, start, h.micros)
        expected = start + h.length
        return h.length
    }
}

/** MP3 (MPEG-1, 2 and 2.5 audio, layers I to III) frames. */
class MpegFramer : FrameFramer(MpegAudio::header, 4)

/** AAC in ADTS frames, as internet radios send AAC and AAC+. */
class AdtsFramer : FrameFramer(Adts::header, 7)

/** ID3v2 tags in a stream. */
internal object Id3 {
    /**
     * How long the ID3v2 tag at [from] is, header included; 0 when there is no tag there, -1 when
     * the tag's header is not all there yet.
     */
    fun skipLength(data: ByteArray, from: Int, end: Int): Int {
        if (end - from < 3) return 0
        if (data[from] != 'I'.code.toByte() || data[from + 1] != 'D'.code.toByte() || data[from + 2] != '3'.code.toByte()) return 0
        if (end - from < 10) return -1
        val size = (0..3).fold(0) { acc, k ->
            val b = data[from + 6 + k].toInt()
            if (b and 0x80 != 0) return 0
            (acc shl 7) or b
        }
        val footer = if (data[from + 5].toInt() and 0x10 != 0) 10 else 0
        return 10 + size + footer
    }
}

/** MPEG audio frame headers. */
internal object MpegAudio {
    private val V1_L1 = intArrayOf(32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val V1_L2 = intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val V1_L3 = intArrayOf(32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val V2_L1 = intArrayOf(32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    private val V2_L23 = intArrayOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val RATES = intArrayOf(44100, 48000, 32000)

    fun header(b: ByteArray, i: Int, end: Int): FrameHeader? {
        if (i + 4 > end) return null
        val b1 = b[i + 1].toInt() and 0xFF
        val b2 = b[i + 2].toInt() and 0xFF
        if (b[i] != 0xFF.toByte() || b1 and 0xE0 != 0xE0) return null
        // 3: MPEG-1, 2: MPEG-2, 0: MPEG-2.5, 1: reserved
        val version = (b1 shr 3) and 3
        // 3: layer I, 2: layer II, 1: layer III, 0: reserved
        val layer = (b1 shr 1) and 3
        if (version == 1 || layer == 0) return null
        val bitrateIndex = (b2 shr 4) and 0xF
        val rateIndex = (b2 shr 2) and 3
        if (bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
        val padding = (b2 shr 1) and 1
        val v1 = version == 3
        val rate = RATES[rateIndex] shr (if (v1) 0 else if (version == 2) 1 else 2)
        val table = when {
            v1 && layer == 3 -> V1_L1
            v1 && layer == 2 -> V1_L2
            v1 -> V1_L3
            layer == 3 -> V2_L1
            else -> V2_L23
        }
        val bitrate = table[bitrateIndex - 1] * 1000
        val samples = if (layer == 3) 384 else if (layer == 2 || v1) 1152 else 576
        val length = when {
            layer == 3 -> (12 * bitrate / rate + padding) * 4
            layer == 2 || v1 -> 144 * bitrate / rate + padding
            else -> 72 * bitrate / rate + padding
        }
        return FrameHeader(length, samples * 1_000_000L / rate)
    }
}

/** ADTS (AAC) frame headers. */
internal object Adts {
    private val RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000)

    fun header(b: ByteArray, i: Int, end: Int): FrameHeader? {
        if (i + 7 > end) return null
        val b1 = b[i + 1].toInt() and 0xFF
        // twelve bits of sync, the layer bits zero
        if (b[i] != 0xFF.toByte() || b1 and 0xF6 != 0xF0) return null
        val rateIndex = (b[i + 2].toInt() shr 2) and 0xF
        if (rateIndex >= RATES.size) return null
        val length = ((b[i + 3].toInt() and 3) shl 11) or ((b[i + 4].toInt() and 0xFF) shl 3) or ((b[i + 5].toInt() and 0xFF) shr 5)
        val headerLength = if (b1 and 1 == 1) 7 else 9
        if (length < headerLength) return null
        val blocks = (b[i + 6].toInt() and 3) + 1
        return FrameHeader(length, 1024L * blocks * 1_000_000L / RATES[rateIndex])
    }
}
