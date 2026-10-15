package app.nagmedar.ui.radio.record

import java.util.Locale

/**
 * S-1107: cuts an Ogg stream (Vorbis or Opus) into pages. A page is trusted when its CRC holds.
 * Icecast starts a new logical stream (a page marked "beginning of stream", with new headers) for
 * each track of a chained stream, and puts the track's artist and title in the Vorbis comments of
 * its headers: the pages of a new stream are held back until the comments have been read, and the
 * first of them is handed over with [MediaUnit.startsTrack] and the [TrackTag] the comments name.
 * Ogg tracks are cut where the station chains them, not by ICY titles.
 */
class OggFramer : Framer() {
    private var holding = false
    private val held = ArrayList<MediaUnit>()
    private var headBytes = ByteArray(0)
    private var rate = 0
    private var lastGranule = 0L

    override fun step(data: ByteArray, from: Int, end: Int, final: Boolean, start: Long, sink: UnitSink): Int {
        if (data[from] != 'O'.code.toByte()) {
            var j = from + 1
            while (j < end && data[j] != 'O'.code.toByte()) j++
            return j - from
        }
        if (end - from < 27) return if (final) end - from else 0
        if (data[from + 1] != 'g'.code.toByte() || data[from + 2] != 'g'.code.toByte() || data[from + 3] != 'S'.code.toByte() || data[from + 4].toInt() != 0) return 1
        val segments = data[from + 26].toInt() and 0xFF
        val headerLength = 27 + segments
        if (end - from < headerLength) return if (final) end - from else 0
        var bodyLength = 0
        for (k in 0 until segments) bodyLength += data[from + 27 + k].toInt() and 0xFF
        val total = headerLength + bodyLength
        if (end - from < total) return if (final) end - from else 0
        if (!crcHolds(data, from, total)) return 1
        val flags = data[from + 5].toInt() and 0xFF
        val granule = longAt(data, from + 6)
        val bos = flags and 0x02 != 0
        if (bos) {
            // a new logical stream: its headers come first
            release(sink)
            holding = true
            headBytes = ByteArray(0)
            rate = rateOf(data, from + headerLength, bodyLength)
            lastGranule = 0L
        }
        var micros = 0L
        if (granule >= 0 && rate > 0) {
            if (lastGranule >= 0 && granule > lastGranule) micros = (granule - lastGranule) * 1_000_000L / rate
            lastGranule = granule
        }
        val page = MediaUnit(data.copyOfRange(from, from + total), start, micros, startsTrack = bos)
        if (holding) {
            held += page
            if (headBytes.size < HEAD_LIMIT) headBytes += data.copyOfRange(from + headerLength, from + total)
            val tag = VorbisComments.tag(headBytes)
            // audio has begun (granule above 0) or enough pages came without comments: nothing more to wait for
            if (tag != null || granule > 0 || held.size >= MAX_HELD) {
                handOver(sink, tag)
            }
        } else {
            sink.unit(page.data, 0, page.data.size, page.start, page.micros, page.startsTrack)
        }
        return total
    }

    override fun release(sink: UnitSink) {
        if (holding) handOver(sink, VorbisComments.tag(headBytes))
    }

    private fun handOver(sink: UnitSink, tag: TrackTag?) {
        holding = false
        held.forEachIndexed { index, page ->
            sink.unit(page.data, 0, page.data.size, page.start, page.micros, startsTrack = index == 0, tag = if (index == 0) tag else null)
        }
        held.clear()
    }

    private companion object {
        const val MAX_HELD = 4
        const val HEAD_LIMIT = 16 * 1024

        fun longAt(b: ByteArray, i: Int): Long {
            var v = 0L
            for (k in 7 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
            return v
        }

        /** The sample rate a stream's identification packet gives: Vorbis writes it, Opus counts in 48 kHz. */
        fun rateOf(b: ByteArray, from: Int, length: Int): Int {
            if (length >= 16 && b[from].toInt() == 1 && bytesAt(b, from + 1, "vorbis")) {
                return (b[from + 12].toInt() and 0xFF) or ((b[from + 13].toInt() and 0xFF) shl 8) or
                    ((b[from + 14].toInt() and 0xFF) shl 16) or ((b[from + 15].toInt() and 0xFF) shl 24)
            }
            if (length >= 8 && bytesAt(b, from, "OpusHead")) return 48_000
            return 0
        }

        private val CRC = IntArray(256) { n ->
            var c = n shl 24
            repeat(8) { c = if (c < 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
            c
        }

        /** Whether the CRC in the page at [from] (checksum field counted as zero) is the page's. */
        fun crcHolds(b: ByteArray, from: Int, length: Int): Boolean {
            var crc = 0
            for (k in 0 until length) {
                val byte = if (k in 22..25) 0 else b[from + k].toInt() and 0xFF
                crc = (crc shl 8) xor CRC[((crc ushr 24) and 0xFF) xor byte]
            }
            val stored = (b[from + 22].toInt() and 0xFF) or ((b[from + 23].toInt() and 0xFF) shl 8) or
                ((b[from + 24].toInt() and 0xFF) shl 16) or ((b[from + 25].toInt() and 0xFF) shl 24)
            return crc == stored
        }
    }
}

/** S-1107: the artist and title in the comment header of a Vorbis or Opus stream. */
object VorbisComments {

    /** The track [head] (the first bytes of a logical stream's pages) names; null when the comments are not there or name neither. */
    fun tag(head: ByteArray): TrackTag? {
        val at = find(head)
        if (at < 0) return null
        var i = at
        val vendor = int(head, i) ?: return null
        i += 4
        if (vendor < 0 || i + vendor > head.size) return null
        i += vendor
        val count = int(head, i) ?: return null
        i += 4
        var artist: String? = null
        var title: String? = null
        var read = 0
        while (read < count && read < MAX_COMMENTS) {
            val length = int(head, i) ?: break
            i += 4
            if (length < 0 || i + length > head.size) break
            val field = String(head, i, length, Charsets.UTF_8)
            i += length
            read++
            val eq = field.indexOf('=')
            if (eq <= 0) continue
            val value = field.substring(eq + 1).trim().ifEmpty { null } ?: continue
            when (field.substring(0, eq).uppercase(Locale.ROOT)) {
                "ARTIST" -> if (artist == null) artist = value
                "TITLE" -> if (title == null) title = value
            }
        }
        return if (artist == null && title == null) null else TrackTag(artist, title)
    }

    /** Where the comments' vendor length starts: after "\u0003vorbis" or "OpusTags"; -1 when neither is there. */
    private fun find(head: ByteArray): Int {
        for (i in 0 until head.size - 8) {
            if (head[i].toInt() == 3 && bytesAt(head, i + 1, "vorbis")) return i + 7
            if (bytesAt(head, i, "OpusTags")) return i + 8
        }
        return -1
    }

    private fun int(b: ByteArray, i: Int): Int? {
        if (i + 4 > b.size) return null
        return (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
    }

    private const val MAX_COMMENTS = 256
}

/** Whether the ASCII [word] is at [at] in [b]. */
internal fun bytesAt(b: ByteArray, at: Int, word: String): Boolean {
    if (at < 0 || at + word.length > b.size) return false
    for (k in word.indices) if (b[at + k].toInt() != word[k].code) return false
    return true
}
