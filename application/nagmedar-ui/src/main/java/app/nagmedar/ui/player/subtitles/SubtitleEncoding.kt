package app.nagmedar.ui.player.subtitles

import androidx.annotation.StringRes
import app.nagmedar.ui.R
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * The character sets a subtitle file is read in (S-702): detected, or chosen by hand for a film.
 * [vlc] is VLC's name for it ("subsdec-encoding"), [java] Java's.
 */
enum class SubtitleEncoding(val key: String, val vlc: String, val java: String, @StringRes val label: Int) {
    Utf8("utf-8", "UTF-8", "UTF-8", R.string.nag_enc_utf8),
    Windows1254("windows-1254", "Windows-1254", "windows-1254", R.string.nag_enc_tr_windows),
    Iso88599("iso-8859-9", "ISO-8859-9", "ISO-8859-9", R.string.nag_enc_tr_iso),
    Windows1252("windows-1252", "Windows-1252", "windows-1252", R.string.nag_enc_western),
    Windows1250("windows-1250", "Windows-1250", "windows-1250", R.string.nag_enc_central),
    Windows1251("windows-1251", "Windows-1251", "windows-1251", R.string.nag_enc_cyrillic),
    Windows1253("windows-1253", "Windows-1253", "windows-1253", R.string.nag_enc_greek),
    Windows1256("windows-1256", "Windows-1256", "windows-1256", R.string.nag_enc_arabic),

    /** Only with its byte order mark: a file's own say. */
    Utf16("utf-16", "UTF-16", "UTF-16", R.string.nag_enc_utf16),
    ;

    val charset: Charset get() = Charset.forName(java)

    /** Whether the bytes are Unicode: VLC reads them so whatever it is told. */
    val unicode: Boolean get() = this == Utf8 || this == Utf16

    companion object {
        fun of(key: String?): SubtitleEncoding? = entries.firstOrNull { it.key == key }

        /** What the user may choose, in the order the dialog lists them. */
        val choices: List<SubtitleEncoding> = listOf(Utf8, Windows1254, Iso88599, Windows1252, Windows1250, Windows1251, Windows1253, Windows1256)
    }
}

/**
 * The letters only Turkish has among Windows-1254's and ISO-8859-9's: Ğ İ Ş ğ ı ş. Western
 * European code pages have Icelandic letters there (Ð Ý Þ ð ý þ), seldom seen elsewhere.
 */
private val turkishBytes = setOf(0xD0, 0xDD, 0xDE, 0xF0, 0xFD, 0xFE)

/**
 * What a subtitle file's bytes are written in: its byte order mark, else UTF-8 if they read so,
 * else a code page by its letters: Turkish's own letters (Windows-1254, or ISO-8859-9 when no
 * Windows punctuation is there), a text mostly of high bytes (Cyrillic, Windows-1251), else
 * Windows-1252.
 */
fun detectEncoding(bytes: ByteArray): SubtitleEncoding {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return SubtitleEncoding.Utf8
    if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))) {
        return SubtitleEncoding.Utf16
    }
    if (bytes.all { it >= 0 } || readsAsUtf8(bytes)) return SubtitleEncoding.Utf8
    var high = 0
    var letters = 0
    var turkish = 0
    var windowsOnly = 0
    for (byte in bytes) {
        val b = byte.toInt() and 0xFF
        when {
            b in 0x80..0x9F -> windowsOnly++
            b >= 0xC0 -> {
                high++
                letters++
                if (b in turkishBytes) turkish++
            }
            b in 0x41..0x5A || b in 0x61..0x7A -> letters++
        }
    }
    return when {
        letters > 0 && high * 2 > letters -> SubtitleEncoding.Windows1251
        turkish > 0 && windowsOnly > 0 -> SubtitleEncoding.Windows1254
        turkish > 0 -> SubtitleEncoding.Iso88599
        else -> SubtitleEncoding.Windows1252
    }
}

/** Whether [bytes] are UTF-8; a character cut at their end (a file's head) still counts. */
private fun readsAsUtf8(bytes: ByteArray): Boolean {
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes, 0, wholeLength(bytes)))
        true
    } catch (e: CharacterCodingException) {
        false
    }
}

/** How many of [bytes] end with a whole UTF-8 character: a head read in blocks may end inside one. */
private fun wholeLength(bytes: ByteArray): Int {
    var start = bytes.size
    var continuations = 0
    while (continuations < 3 && start > 0 && (bytes[start - 1].toInt() and 0xC0) == 0x80) {
        start--
        continuations++
    }
    if (start == 0) return bytes.size
    val lead = bytes[start - 1].toInt() and 0xFF
    val length = when {
        lead >= 0xF0 -> 4
        lead >= 0xE0 -> 3
        lead >= 0xC0 -> 2
        else -> 1
    }
    return if (length > 1 && continuations + 1 < length) start - 1 else bytes.size
}

/** [bytes] as text in [encoding], its byte order mark left out. */
fun decodeSubtitle(bytes: ByteArray, encoding: SubtitleEncoding): String {
    val text = String(bytes, encoding.charset)
    return text.removePrefix("\uFEFF")
}

/** The extensions of the subtitle formats written as text, which can be read in another character set. */
private val textFormats = setOf("srt", "ass", "ssa", "vtt", "smi", "sami", "sub", "txt")

/** Whether a subtitle file is text that can be read again (an .idx and its .sub are VobSub's pictures). */
fun isTextSubtitle(name: String, head: ByteArray): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.ROOT) in textFormats && head.none { it == 0.toByte() }

/**
 * Lines of a subtitle's text to see its letters by: the said lines of an SRT, a VTT, a MicroDVD
 * or an ASS (not the numbers, the times, the styles), those with letters beyond ASCII first.
 */
fun previewLines(text: String, count: Int = 3): List<String> {
    val lines = text.lineSequence().map { it.trim() }
    val ass = text.contains("[Script Info]", ignoreCase = true) || text.contains("\nDialogue:", ignoreCase = true)
    val said = if (ass) {
        lines.filter { it.startsWith("Dialogue:", ignoreCase = true) }
            .mapNotNull { line -> line.split(',', limit = 10).getOrNull(9)?.replace(assTags, "")?.replace("\\N", " ")?.replace("\\n", " ")?.trim() }
    } else {
        lines.filter { line -> line.isNotEmpty() && "-->" !in line && !line.all { it.isDigit() } && !line.startsWith("WEBVTT") && !line.startsWith("NOTE") }
            .map { line -> line.replace(microDvdTimes, "").replace(htmlTags, "").replace('|', ' ').trim() }
    }
    val found = said.filter { line -> line.any { it.isLetter() } }.take(PREVIEW_SCAN).toList()
    val rich = found.filter { line -> line.any { it.code > 0x7F } }
    return (rich + found.filterNot { it in rich }).take(count)
}

private const val PREVIEW_SCAN = 200
private val assTags = Regex("""\{[^}]*\}""")
private val htmlTags = Regex("""<[^>]+>""")
private val microDvdTimes = Regex("""^\{\d+\}\{\d+\}""")
