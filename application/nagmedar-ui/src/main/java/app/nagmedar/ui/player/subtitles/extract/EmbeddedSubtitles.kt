package app.nagmedar.ui.player.subtitles.extract

import androidx.compose.runtime.Immutable
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.ui.player.subtitles.languageOf
import app.nagmedar.ui.player.subtitles.tools.SubtitleFormat
import java.util.Locale

/**
 * A subtitle track inside a film's file, as the media probe lists it: its place among the
 * film's subtitle tracks ([index], 0 first, which the extraction is told), its codec, language
 * and title. Text tracks are written into a file of their own; picture ones are not (OCR).
 */
@Immutable
data class EmbeddedTrack(
    val index: Int,
    /** FFmpeg's name: "subrip", "ass", "ssa", "webvtt", "mov_text", "hdmv_pgs_subtitle" … */
    val codec: String,
    /** As tagged: "tur", "eng"; empty when untagged. */
    val language: String,
    val title: String,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val isDefault: Boolean = false,
) {
    /** Lines the extraction writes into a file: SubRip, ASS/SSA, WebVTT, MP4 timed text, plain text. */
    val text: Boolean get() = codec in TEXT_CODECS

    /** Lines that are pictures (PGS, VobSub, DVB): they would need OCR. */
    val picture: Boolean get() = codec in PICTURE_CODECS

    /** The file the extraction writes: ASS and SSA stay as they are, everything else becomes SRT. */
    val format: SubtitleFormat
        get() = when (codec) {
            "ass" -> SubtitleFormat.Ass
            "ssa" -> SubtitleFormat.Ssa
            else -> SubtitleFormat.Srt
        }

    /** "SRT", "ASS", "PGS", "VobSub", "MP4" …: the codec as a short name. */
    val codecLabel: String
        get() = when (codec) {
            "subrip", "srt" -> "SRT"
            "ass" -> "ASS"
            "ssa" -> "SSA"
            "webvtt" -> "WebVTT"
            "mov_text" -> "MP4 Timed Text"
            "text" -> "TXT"
            "hdmv_pgs_subtitle" -> "PGS"
            "dvd_subtitle" -> "VobSub"
            "dvb_subtitle" -> "DVB"
            "dvb_teletext" -> "Teletext"
            "xsub" -> "XSUB"
            else -> codec.uppercase(Locale.ROOT)
        }

    companion object {
        val TEXT_CODECS = setOf("subrip", "ass", "ssa", "webvtt", "mov_text", "text")
        val PICTURE_CODECS = setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub")
    }
}

/** The film's subtitle tracks, in the order the extraction counts them. */
fun embeddedTracks(tech: MediaTech?): List<EmbeddedTrack> = tech?.subtitles.orEmpty().mapIndexed { index, s -> s.toTrack(index) }

private fun SubtitleTech.toTrack(index: Int) = EmbeddedTrack(
    index = index,
    codec = codec,
    language = language,
    title = title,
    forced = forced,
    hearingImpaired = hearingImpaired,
    isDefault = isDefault,
)

/**
 * The new file's name for [track] of the film [video] ("Film.mkv"): the film's name, the
 * track's language as the players read it ("tr"; the tag itself when it is not a known one),
 * "forced" or "sdh" when the track is one, and the format's extension: "Film.tr.srt",
 * "Film.en.forced.ass". A file of that name already there makes it "Film.tr (2).srt".
 */
fun extractedName(video: String, track: EmbeddedTrack): String {
    val base = video.substringBeforeLast('.', video).trim().ifEmpty { "subtitles" }
    val tag = track.language.trim().lowercase(Locale.ROOT)
    val language = when {
        tag.isEmpty() || tag in UNTAGGED -> null
        else -> languageOf(tag) ?: tag.takeIf { it.all { c -> c in 'a'..'z' } }
    }
    val parts = listOfNotNull(
        base,
        language,
        "forced".takeIf { track.forced },
        "sdh".takeIf { track.hearingImpaired && !track.forced },
    )
    return parts.joinToString(".") + "." + track.format.extension
}

/** ISO 639-2's "undetermined", "no linguistic content", "multiple" and "uncoded". */
private val UNTAGGED = setOf("und", "zxx", "mul", "mis")
