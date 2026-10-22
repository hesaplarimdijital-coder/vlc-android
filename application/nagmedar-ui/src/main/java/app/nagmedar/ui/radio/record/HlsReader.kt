package app.nagmedar.ui.radio.record

import java.io.IOException

/** S-1107: fetches the files of an HLS stream on a connection of its own: a real one in the app, a fake in the tests. */
interface HlsClient {
    /**
     * The bytes of [url] ([range] only, when given), at most [limit] of them. Blocking. Throws
     * [StreamRefused] for an HTTP error status and another [IOException] when the network fails or
     * [cancel] was called.
     */
    fun fetch(url: String, range: HlsByteRange?, limit: Int): ByteArray

    /** Ends a fetch that waits, from any thread, and refuses all that come after. */
    fun cancel()
}

/** An HLS stream that was opened: the address its playlist was fetched from, the playlist, and the client to fetch the rest with. */
class HlsStart(val url: String, val text: String, val client: HlsClient)

/**
 * S-1107: records an HLS stream. It takes the media playlist (the best sound-only variant of a
 * master), and polls it as often as its target duration says; each new segment is fetched in order
 * and handed to the [Output] as media units:
 *
 *  - packed audio (ADTS AAC or MPEG audio, with the ID3 timestamps HLS puts at the start of a segment) and
 *    MPEG-TS whose program is sound only (AAC or MP3) are cut into frames: the file is `.aac` or `.mp3`;
 *  - other MPEG-TS (pictures, another codec) is kept whole: `.ts`;
 *  - fragmented MP4 (an `EXT-X-MAP` initialisation and fragments) is kept whole: `.m4a`, or `.mp4` with pictures.
 *
 * A segment that is gone (404, 410) or fails again and again is passed over, and the file goes on;
 * a playlist that goes back (the server began again) is taken from its live edge; a playlist that
 * cannot be fetched is tried again with growing waits before it is given up. Encrypted segments
 * (`EXT-X-KEY` with a method other than NONE) are refused: [StreamUnsupported] with
 * [UnsupportedStream.Encrypted]. [run] returns when the stream ends (`EXT-X-ENDLIST`) or [stopped] is true.
 */
class HlsReader(
    private val client: HlsClient,
    private val clock: () -> Long,
    private val pause: suspend (Long) -> Unit,
    private val stopped: () -> Boolean,
) {
    /** Where the segments go. */
    interface Output {
        /** The format of the file is known; called once, before the first unit. */
        fun format(format: AudioFormat)

        /** The next unit of the file. */
        fun unit(unit: MediaUnit)

        /** A segment has been taken in. */
        suspend fun segment()
    }

    private var next: Long? = null
    private var format: AudioFormat? = null
    private var written = 0L
    private var mapWritten: String? = null
    private val tries = HashMap<Long, Int>()
    private val maps = HashMap<String, ByteArray>()
    private var adts: Framer? = null
    private var mpeg: Framer? = null

    /** Segments in a row that were no kind of audio (before the first one that was). */
    private var unknown = 0

    /** Records the stream of the playlist [text] fetched from [url], until it ends or [stopped]. */
    suspend fun run(url: String, text: String, out: Output) {
        var playlistUrl = url
        var parsed = HlsPlaylists.parse(text, url)
        var audioOnly = false
        if (parsed is HlsMaster) {
            val choice = parsed.choose() ?: throw StreamUnsupported(UnsupportedStream.Format)
            playlistUrl = choice.url
            audioOnly = choice.audioOnly
            parsed = HlsPlaylists.parse(String(client.fetch(playlistUrl, null, MAX_PLAYLIST), Charsets.UTF_8), playlistUrl)
        }
        var playlist = parsed as? HlsMedia ?: throw StreamUnsupported(UnsupportedStream.Format)
        if (playlist.keyFramesOnly) throw StreamUnsupported(UnsupportedStream.Format)
        var progressAt = clock()
        while (true) {
            if (stopped()) return
            if (playlist.segments.any { it.encrypted }) throw StreamUnsupported(UnsupportedStream.Encrypted)
            val loadedAt = clock()
            val took = take(playlist, audioOnly, out)
            if (stopped()) return
            if (playlist.ended && (next ?: 0L) > playlist.lastSequence) return
            if (took) progressAt = clock() else if (clock() - progressAt > STALL_MS) throw IOException("the playlist does not move")
            playlist = reload(playlist, playlistUrl, took, loadedAt) ?: return
            if (playlist.keyFramesOnly) throw StreamUnsupported(UnsupportedStream.Format)
        }
    }

    /** Fetches and hands over the segments of [playlist] not taken yet, in order; whether it took any. */
    private suspend fun take(playlist: HlsMedia, audioOnly: Boolean, out: Output): Boolean {
        val first = playlist.mediaSequence
        val last = playlist.lastSequence
        val edge = maxOf(first, last - (START_SEGMENTS - 1))
        val from = when (val n = next) {
            null -> if (playlist.ended) first else edge
            // the server began again: its numbers went back
            else -> if (last < n - 1) edge else maxOf(first, n)
        }
        next = from
        var took = false
        for (segment in playlist.segments) {
            if (segment.sequence < from) continue
            if (stopped()) return took
            val bytes = try {
                client.fetch(segment.url, segment.range, MAX_SEGMENT)
            } catch (e: StreamRefused) {
                // gone from the server: the file goes on without it
                if (e.status == 404 || e.status == 410) {
                    next = segment.sequence + 1
                    continue
                }
                if (again(segment)) return took else continue
            } catch (e: IOException) {
                if (stopped()) return took
                if (again(segment)) return took else continue
            }
            tries.remove(segment.sequence)
            hand(segment, bytes, audioOnly, out)
            next = segment.sequence + 1
            took = true
            out.segment()
        }
        return took
    }

    /** A segment failed: true to try it again when the playlist is reloaded; false once it has failed [SEGMENT_TRIES] times and is passed over. */
    private fun again(segment: HlsSegment): Boolean {
        val count = (tries[segment.sequence] ?: 0) + 1
        if (count < SEGMENT_TRIES) {
            tries[segment.sequence] = count
            return true
        }
        tries.remove(segment.sequence)
        next = segment.sequence + 1
        return false
    }

    /** Waits for the playlist to change and fetches it; null when [stopped]. A playlist that cannot be had is retried, then the failure is thrown. */
    private suspend fun reload(playlist: HlsMedia, url: String, took: Boolean, loadedAt: Long): HlsMedia? {
        val target = (if (playlist.targetSeconds > 0) playlist.targetSeconds else DEFAULT_TARGET_SECONDS) * 1000L
        // a playlist with nothing new is looked at again in half the time
        sleep(maxOf(loadedAt + (if (took) target else target / 2) - clock(), MIN_WAIT_MS))
        var failures = 0
        while (true) {
            if (stopped()) return null
            try {
                val text = String(client.fetch(url, null, MAX_PLAYLIST), Charsets.UTF_8)
                return HlsPlaylists.parse(text, url) as? HlsMedia ?: throw IOException("not a media playlist")
            } catch (e: IOException) {
                if (stopped()) return null
                failures++
                if (failures > MAX_RELOAD_FAILURES) throw e
                sleep(minOf(BACKOFF_MS shl (failures - 1), MAX_BACKOFF_MS))
            }
        }
    }

    private suspend fun sleep(ms: Long) {
        var left = ms
        while (left > 0 && !stopped()) {
            val step = minOf(left, 250L)
            pause(step)
            left -= step
        }
    }

    /** Turns one segment into the file's units. */
    private fun hand(segment: HlsSegment, bytes: ByteArray, audioOnly: Boolean, out: Output) {
        val kind = if (segment.map != null) Kind.Fragment else sniff(bytes)
        var body = bytes
        val target: AudioFormat = when (kind) {
            Kind.Adts -> AudioFormat.Aac
            Kind.Mpeg -> AudioFormat.Mp3
            Kind.TransportStream -> {
                val ts = TransportStream.audio(bytes)
                if (ts.format != null) {
                    body = ts.audio
                    ts.format
                } else {
                    AudioFormat.Ts
                }
            }
            Kind.Fragment -> format?.takeIf { it == AudioFormat.M4a || it == AudioFormat.Mp4 } ?: fragmentFormat(segment, audioOnly)
            Kind.Unknown -> {
                // nothing of this stream can be recorded (subtitles, an unknown codec): it is said, not waited on
                if (format == null && ++unknown >= UNKNOWN_SEGMENTS) throw StreamUnsupported(UnsupportedStream.Format)
                return
            }
        }
        val current = format
        if (current == null) {
            format = target
            out.format(target)
        } else if (current != target) {
            // another kind of segment among them (an advertisement, another codec): not in this file
            return
        }
        if (kind == Kind.Fragment) writeMap(segment.map, out)
        val micros = (segment.seconds * 1_000_000).toLong()
        when (target) {
            AudioFormat.Aac, AudioFormat.Mp3 -> {
                val framer = if (target == AudioFormat.Aac) adts ?: AdtsFramer().also { adts = it } else mpeg ?: MpegFramer().also { mpeg = it }
                framer.feed(body, 0, body.size) { out.unit(it) }
                framer.finish { out.unit(it) }
            }
            else -> unit(body, micros, out)
        }
    }

    private fun unit(data: ByteArray, micros: Long, out: Output) {
        out.unit(MediaUnit(data, written, micros))
        written += data.size
    }

    /** The initialisation of fragmented MP4 goes first, and again when it changes. */
    private fun writeMap(map: HlsMap?, out: Output) {
        if (map == null) return
        val key = "${map.url}#${map.range?.offset}"
        if (key == mapWritten) return
        val bytes = maps[key] ?: client.fetch(map.url, map.range, MAX_SEGMENT).also { maps[key] = it }
        mapWritten = key
        unit(bytes, 0, out)
    }

    /** M4A for sound, MP4 when the initialisation has a video track (or the playlist is a video variant). */
    private fun fragmentFormat(segment: HlsSegment, audioOnly: Boolean): AudioFormat {
        val map = segment.map ?: return AudioFormat.Mp4
        val init = try {
            maps.getOrPut("${map.url}#${map.range?.offset}") { client.fetch(map.url, map.range, MAX_SEGMENT) }
        } catch (e: IOException) {
            return if (audioOnly) AudioFormat.M4a else AudioFormat.Mp4
        }
        return if (hasVideoTrack(init)) AudioFormat.Mp4 else AudioFormat.M4a
    }

    private enum class Kind { Adts, Mpeg, TransportStream, Fragment, Unknown }

    companion object {
        /** How many segments back from the end of a live playlist the recording begins. */
        const val START_SEGMENTS = 2

        /** A segment that fails this many times (one try for each load of the playlist) is passed over. */
        const val SEGMENT_TRIES = 3

        /** A playlist that cannot be fetched this many times in a row ends the connection. */
        const val MAX_RELOAD_FAILURES = 8

        /** A playlist that gives no new segment for this long ends the connection (the recording tries again). */
        private const val STALL_MS = 3 * 60_000L

        /** Segments that are no audio at all, in a row, before one that is, give up the stream. */
        private const val UNKNOWN_SEGMENTS = 3

        private const val DEFAULT_TARGET_SECONDS = 6
        private const val MIN_WAIT_MS = 500L
        private const val BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 10_000L
        private const val MAX_PLAYLIST = 2 * 1024 * 1024
        private const val MAX_SEGMENT = 32 * 1024 * 1024

        /** What kind of segment [data] is. */
        private fun sniff(data: ByteArray): Kind {
            val n = data.size
            if (TransportStream.matches(data, n)) return Kind.TransportStream
            val start = Id3.skipLength(data, 0, n).coerceAtLeast(0)
            if (start >= n) return Kind.Unknown
            if (chained(data, start, n) { b, i, e -> Adts.header(b, i, e) }) return Kind.Adts
            if (chained(data, start, n) { b, i, e -> MpegAudio.header(b, i, e) }) return Kind.Mpeg
            if (n >= 12 && BOXES.any { box -> (0 until 4).all { data[4 + it] == box[it].code.toByte() } }) return Kind.Fragment
            return Kind.Unknown
        }

        private val BOXES = listOf("ftyp", "styp", "moof", "moov", "sidx")

        /** A frame header at [at] that is followed by another (or by the end of the data): a chance 0xFF 0xF1 in other data is not. */
        private fun chained(data: ByteArray, at: Int, end: Int, header: (ByteArray, Int, Int) -> FrameHeader?): Boolean {
            val h = header(data, at, end) ?: return false
            val next = at + h.length
            return next + 4 > end || header(data, next, end) != null
        }

        /** Whether an MP4 initialisation has a video track: the `hdlr` box of one says `vide`. */
        internal fun hasVideoTrack(init: ByteArray): Boolean {
            val handler = "hdlr".toByteArray(Charsets.ISO_8859_1)
            val video = "vide".toByteArray(Charsets.ISO_8859_1)
            var i = 0
            while (i + 16 <= init.size) {
                if (init[i] == handler[0] && init[i + 1] == handler[1] && init[i + 2] == handler[2] && init[i + 3] == handler[3]) {
                    // version and flags (4), predefined (4), then the handler type
                    if ((0 until 4).all { init[i + 12 + it] == video[it] }) return true
                }
                i++
            }
            return false
        }
    }
}
