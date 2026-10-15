package app.nagmedar.ui.radio.record

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

/** S-1107: streams for the tests, built the way stations send them. */
object Streams {

    /** An MPEG-1 layer III frame at 128 kbit/s, 44.1 kHz: 417 bytes, 26.122 ms. [id] (1..250) is written in the body so a test can tell the frames apart. */
    fun mp3Frame(id: Int): ByteArray {
        val frame = ByteArray(417) { 0x55 }
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00
        frame[4] = id.toByte()
        return frame
    }

    /** [count] MP3 frames numbered from [first]. */
    fun mp3(first: Int, count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (i in 0 until count) out.write(mp3Frame((first + i) % 250 + 1))
        return out.toByteArray()
    }

    /** An ADTS frame of [length] bytes (44.1 kHz, stereo, one block: 23.2 ms). */
    fun adtsFrame(id: Int, length: Int = 300): ByteArray {
        val frame = ByteArray(length) { 0x33 }
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF1.toByte()
        frame[2] = 0x50
        frame[3] = (0x80 or ((length shr 11) and 3)).toByte()
        frame[4] = ((length shr 3) and 0xFF).toByte()
        frame[5] = (((length and 7) shl 5) or 0x1F).toByte()
        frame[6] = 0xFC.toByte()
        frame[7] = id.toByte()
        return frame
    }

    fun adts(first: Int, count: Int): ByteArray {
        val out = ByteArrayOutputStream()
        for (i in 0 until count) out.write(adtsFrame((first + i) % 250 + 1))
        return out.toByteArray()
    }

    /** An ICY metadata block for [title]: the length byte and the text padded with zeros. */
    fun icyBlock(title: String?, charset: java.nio.charset.Charset = Charsets.UTF_8): ByteArray {
        if (title == null) return byteArrayOf(0)
        val text = "StreamTitle='$title';".toByteArray(charset)
        val units = (text.size + 15) / 16
        return byteArrayOf(units.toByte()) + text.copyOf(units * 16)
    }

    /**
     * [audio] with a metadata block after every [metaInt] bytes: [titles] gives the title of the
     * n-th block (null: an empty block, "no change").
     */
    fun icy(audio: ByteArray, metaInt: Int, titles: (Int) -> String?): ByteArray {
        val out = ByteArrayOutputStream()
        var block = 0
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + metaInt, audio.size)
            out.write(audio, i, end - i)
            if (end - i == metaInt) out.write(icyBlock(titles(block++)))
            i = end
        }
        return out.toByteArray()
    }

    private val CRC = IntArray(256) { n ->
        var c = n shl 24
        repeat(8) { c = if (c < 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
        c
    }

    /** An Ogg page with [body] in one segment run, granule [granule], flags [flags] (2: first page of a stream), serial and sequence given, with its CRC. */
    fun oggPage(body: ByteArray, granule: Long, flags: Int, serial: Int = 1, sequence: Int = 0): ByteArray {
        val segments = ArrayList<Int>()
        var left = body.size
        while (left >= 255) {
            segments += 255
            left -= 255
        }
        segments += left
        val header = ByteArray(27 + segments.size)
        "OggS".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        header[5] = flags.toByte()
        for (k in 0 until 8) header[6 + k] = ((granule shr (8 * k)) and 0xFF).toByte()
        for (k in 0 until 4) header[14 + k] = ((serial shr (8 * k)) and 0xFF).toByte()
        for (k in 0 until 4) header[18 + k] = ((sequence shr (8 * k)) and 0xFF).toByte()
        header[26] = segments.size.toByte()
        segments.forEachIndexed { i, s -> header[27 + i] = s.toByte() }
        val page = header + body
        var crc = 0
        for (b in page) crc = (crc shl 8) xor CRC[((crc ushr 24) and 0xFF) xor (b.toInt() and 0xFF)]
        for (k in 0 until 4) page[22 + k] = ((crc shr (8 * k)) and 0xFF).toByte()
        return page
    }

    /** The identification packet of a Vorbis stream at 44.1 kHz. */
    fun vorbisId(): ByteArray {
        val packet = ByteArray(30)
        packet[0] = 1
        "vorbis".forEachIndexed { i, c -> packet[1 + i] = c.code.toByte() }
        val rate = 44100
        for (k in 0 until 4) packet[12 + k] = ((rate shr (8 * k)) and 0xFF).toByte()
        return packet
    }

    /** A Vorbis comment packet (type 3) with [comments]. */
    fun vorbisComments(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(3)
        out.write("vorbis".toByteArray())
        val vendor = "test".toByteArray()
        out.write(le(vendor.size))
        out.write(vendor)
        out.write(le(comments.size))
        comments.forEach {
            val b = it.toByteArray()
            out.write(le(b.size))
            out.write(b)
        }
        out.write(1)
        return out.toByteArray()
    }

    /** An Opus tags packet with [comments]. */
    fun opusTags(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("OpusTags".toByteArray())
        val vendor = "test".toByteArray()
        out.write(le(vendor.size))
        out.write(vendor)
        out.write(le(comments.size))
        comments.forEach {
            val b = it.toByteArray()
            out.write(le(b.size))
            out.write(b)
        }
        return out.toByteArray()
    }

    private fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /**
     * A logical Vorbis stream (serial [serial]): its identification page, a comments page with
     * [comments], and [audioPages] pages of audio, each 1 000 samples long.
     */
    fun vorbisStream(serial: Int, vararg comments: String, audioPages: Int = 40): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(oggPage(vorbisId(), 0, 2, serial, 0))
        out.write(oggPage(vorbisComments(*comments), 0, 0, serial, 1))
        for (i in 0 until audioPages) out.write(oggPage(ByteArray(100) { (i + 1).toByte() }, 1000L * (i + 1), 0, serial, 2 + i))
        return out.toByteArray()
    }
}

/** A stream source that gives [chunks] one by one, then calls [onEnd] and ends ([failAfter]: then throws instead). */
class ScriptedSource(
    chunks: List<ByteArray>,
    private val failAfter: Boolean = false,
    private val onEnd: () -> Unit = {},
) : StreamSource {
    private val queue = ArrayDeque(chunks)
    var closed = false
        private set

    override fun read(buffer: ByteArray): Int {
        if (closed) throw IOException("closed")
        val chunk = queue.removeFirstOrNull() ?: run {
            onEnd()
            return if (failAfter) throw IOException("reset") else -1
        }
        val n = minOf(chunk.size, buffer.size)
        System.arraycopy(chunk, 0, buffer, 0, n)
        if (n < chunk.size) queue.addFirst(chunk.copyOfRange(n, chunk.size))
        return n
    }

    override fun close() {
        closed = true
    }
}

/** A source that waits for bytes pushed by the test until it is closed. */
class LiveSource : StreamSource {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val end = ByteArray(0)
    private var leftover: ByteArray? = null

    @Volatile
    var closed = false
        private set

    fun push(bytes: ByteArray) {
        queue.put(bytes)
    }

    override fun read(buffer: ByteArray): Int {
        if (closed) throw IOException("closed")
        val chunk = leftover ?: queue.take()
        leftover = null
        if (chunk === end) throw IOException("closed")
        val n = minOf(chunk.size, buffer.size)
        System.arraycopy(chunk, 0, buffer, 0, n)
        if (n < chunk.size) leftover = chunk.copyOfRange(n, chunk.size)
        return n
    }

    override fun close() {
        closed = true
        queue.put(end)
    }
}

/** Files in memory: the folder tree of a storage, with room that can be set. */
class MemoryStorage(var free: Long = -1L) : TrackStorage {
    class File(val folder: String, val name: String, val mime: String) {
        val bytes = ByteArrayOutputStream()
        var closed = false
        var deleted = false
    }

    val files = ArrayList<File>()
    var writable = true
    var failWrites = false

    /** The files that are still there. */
    val kept: List<File> get() = files.filter { !it.deleted }

    override fun writable(): Boolean = writable

    override fun names(folder: String): Set<String> = kept.filter { it.folder == folder }.map { it.name.lowercase() }.toSet()

    override fun create(folder: String, name: String, mime: String): TrackOutput {
        if (!writable) throw IOException("no folder")
        val file = File(folder, name, mime)
        files += file
        return object : TrackOutput {
            override val uri = "mem://$folder/$name"
            override val name = name
            override val size: Long get() = file.bytes.size().toLong()

            override fun write(data: ByteArray, offset: Int, length: Int) {
                if (failWrites) throw IOException("disk full")
                file.bytes.write(data, offset, length)
            }

            override fun freeBytes(): Long = free

            override fun close() {
                file.closed = true
            }

            override fun delete() {
                file.deleted = true
            }
        }
    }

    override fun freeBytes(): Long = free
}

/** The recordings kept, in memory. */
class MemoryLibrary : SessionLibrary {
    val tracks = ArrayList<RecordedTrack>()
    var pruneFreed = 0L
    val pruned = ArrayList<Pair<Long, Set<String>>>()

    override suspend fun add(track: RecordedTrack): Long {
        tracks += track.copy(id = tracks.size + 1L)
        return tracks.size.toLong()
    }

    override suspend fun usedBytes(): Long = tracks.sumOf { it.sizeBytes }

    override suspend fun prune(bytes: Long, keep: Set<String>): Long {
        pruned += bytes to keep
        return pruneFreed
    }
}

/** A stream opener that gives what the test scripted, one answer for each time it is asked. */
class FakeOpener : StreamOpener {
    val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val answers = java.util.concurrent.ConcurrentLinkedQueue<(String) -> OpenedStream>()

    fun then(answer: (String) -> OpenedStream): FakeOpener {
        answers.add(answer)
        return this
    }

    override suspend fun open(url: String): OpenedStream {
        asked += url
        val answer = answers.poll() ?: throw IOException("nothing to answer with")
        return answer(url)
    }
}

/** A clock the test sets. */
class ManualClock(var now: Long = 1_792_063_805_000L) {
    operator fun invoke(): Long = now
}
