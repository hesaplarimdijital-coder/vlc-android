package app.nagmedar.ui.radio.record

import java.io.ByteArrayOutputStream

/**
 * S-1107: a small ID3v2.4 tag for the start of a recorded MP3 or AAC (ADTS) file: artist, title,
 * album (the station), the date, and the station's name as the radio-station frame. Text is UTF-8,
 * which v2.4 allows. The audio after it is the stream's own bytes.
 */
object Id3Tag {

    /** The tag's bytes. A field that is null or blank is left out; [date] is `2026-10-15`. */
    fun build(artist: String?, title: String?, album: String?, date: String?, station: String?): ByteArray {
        val frames = ByteArrayOutputStream()
        frame(frames, "TIT2", title)
        frame(frames, "TPE1", artist)
        frame(frames, "TALB", album)
        frame(frames, "TDRC", date)
        frame(frames, "TRSN", station)
        val body = frames.toByteArray()
        val out = ByteArrayOutputStream(10 + body.size)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0))
        out.write(syncSafe(body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun frame(out: ByteArrayOutputStream, id: String, text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(syncSafe(bytes.size + 1))
        out.write(byteArrayOf(0, 0))
        // text encoding 3: UTF-8
        out.write(3)
        out.write(bytes)
    }

    /** [value] in four bytes of seven bits. */
    private fun syncSafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
