package app.nagmedar.ui.radio.record

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * S-1107: takes the in-band metadata out of an ICY stream. A server that was asked for it
 * (`Icy-MetaData: 1`) says in `icy-metaint` how many audio bytes come between two metadata blocks;
 * a block is one length byte (the text's length in 16-byte units) and the text, padded with zeros.
 * The audio goes on to [Listener.audio] without a byte of metadata in it, so the file written from
 * it is the stream as the station encoded it. A block can come split over any number of reads. A
 * [metaInt] of zero or less means the server sends no metadata: everything is audio.
 */
class IcyDemuxer(private val metaInt: Int, private val listener: Listener) {

    interface Listener {
        /** [length] bytes of audio from [offset] of [data]. */
        fun audio(data: ByteArray, offset: Int, length: Int)

        /** A metadata block of [length] bytes (the padding included) that came after [audioOffset] bytes of audio. */
        fun metadata(block: ByteArray, length: Int, audioOffset: Long)
    }

    /** How many audio bytes have been handed on. */
    var audioBytes = 0L
        private set

    private var untilBlock = metaInt
    private var blockLeft = 0
    private var blockFilled = 0
    private val block = ByteArray(MAX_BLOCK)

    /** Takes [length] bytes of the stream from [offset] of [data]. */
    fun feed(data: ByteArray, offset: Int, length: Int) {
        if (metaInt <= 0) {
            if (length > 0) {
                listener.audio(data, offset, length)
                audioBytes += length
            }
            return
        }
        var i = offset
        val end = offset + length
        while (i < end) {
            when {
                blockLeft > 0 -> {
                    val take = minOf(blockLeft, end - i)
                    System.arraycopy(data, i, block, blockFilled, take)
                    blockFilled += take
                    blockLeft -= take
                    i += take
                    if (blockLeft == 0) {
                        listener.metadata(block, blockFilled, audioBytes)
                        blockFilled = 0
                        untilBlock = metaInt
                    }
                }
                untilBlock == 0 -> {
                    // the length byte is 0..255 sixteens: at most 4080 bytes, whatever the server means
                    val size = (data[i].toInt() and 0xFF) * 16
                    i++
                    if (size == 0) {
                        untilBlock = metaInt
                    } else {
                        blockLeft = size
                        blockFilled = 0
                    }
                }
                else -> {
                    val take = minOf(untilBlock, end - i)
                    listener.audio(data, i, take)
                    audioBytes += take
                    untilBlock -= take
                    i += take
                }
            }
        }
    }

    companion object {
        /** The largest block a length byte can announce. */
        const val MAX_BLOCK = 255 * 16

        /** The metaint the response headers announce; 0 (no metadata) when there is none or it is no positive number. */
        fun metaInt(header: String?): Int = header?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }
}

/** S-1107: the text of an ICY metadata block and the title in it. */
object StreamTitles {

    /** What a title in a legacy encoding is read as: Turkish stations send Windows-1254 (Latin-1 where it is missing). */
    private val LEGACY: Charset = if (Charset.isSupported("windows-1254")) Charset.forName("windows-1254") else Charsets.ISO_8859_1

    /** The block's text: UTF-8 when it is valid UTF-8, else the legacy encoding; the zero padding is dropped. */
    fun decode(bytes: ByteArray, length: Int, legacy: Charset = LEGACY): String {
        var end = length.coerceIn(0, bytes.size)
        while (end > 0 && bytes[end - 1].toInt() == 0) end--
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, end))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, 0, end, legacy)
        }
    }

    private val NEXT_FIELD = Regex("""^[A-Za-z]+='""")

    /**
     * The value of `StreamTitle='…';` in a block's [text]; null when the block has no such field,
     * "" when the field is empty. A title may hold a quote (`Don't Stop`): the value ends at the
     * first `';` that is followed by another field or by nothing.
     */
    fun title(text: String): String? {
        val key = "StreamTitle='"
        val at = text.indexOf(key)
        if (at < 0) return null
        val from = at + key.length
        var search = from
        while (true) {
            val quote = text.indexOf("';", search)
            if (quote < 0) break
            val rest = text.substring(quote + 2)
            if (rest.isBlank() || NEXT_FIELD.containsMatchIn(rest)) return text.substring(from, quote)
            search = quote + 1
        }
        // a block that is not closed: up to its last quote, else all of it
        val last = text.lastIndexOf('\'')
        return text.substring(from, if (last >= from) last else text.length)
    }
}
