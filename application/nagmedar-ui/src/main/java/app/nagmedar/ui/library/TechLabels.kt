package app.nagmedar.ui.library

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech
import java.text.NumberFormat
import java.util.Locale

/*
 * S-802: the words of the technical facts. Codec and format names are the same in every
 * language; numbers follow the locale ("58,4 GB" in Turkish) and languages are shortened from
 * their localized names ("Tür", "İng").
 */

/** The track a player starts with: the default one, else the first that is not a commentary. */
fun MediaTech.mainAudio(): AudioTech? = audio.firstOrNull { it.isDefault && !it.commentary } ?: audio.firstOrNull { !it.commentary } ?: audio.firstOrNull()

/** "TrueHD", "DTS-HD MA", "DTS:X", "E-AC-3", "AC-3", "AAC", "FLAC", "PCM" … */
fun audioCodecLabel(track: AudioTech): String = when {
    track.dtsX -> "DTS:X"
    track.codec == "dts" -> when {
        track.profile.startsWith("DTS-HD MA") -> "DTS-HD MA"
        track.profile.startsWith("DTS-HD HRA") -> "DTS-HD HRA"
        track.profile == "DTS Express" -> "DTS Express"
        track.profile == "DTS-ES" -> "DTS-ES"
        else -> "DTS"
    }
    else -> when (track.codec) {
        "truehd" -> "TrueHD"
        "mlp" -> "MLP"
        "eac3" -> "E-AC-3"
        "ac3" -> "AC-3"
        "aac", "aac_latm" -> "AAC"
        "flac" -> "FLAC"
        "alac" -> "ALAC"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "mp3", "mp3float" -> "MP3"
        "mp2" -> "MP2"
        "ape" -> "APE"
        "wavpack" -> "WavPack"
        else -> if (track.codec.startsWith("pcm_")) "PCM" else track.codec.uppercase(Locale.ROOT)
    }
}

/**
 * What a music player shows of a track: "FLAC 24/96", "PCM 16/44.1" for lossless ones, "MP3 320
 * kb/s" for lossy ones, the codec alone when the rest is unknown.
 */
fun audioFormatLabel(track: AudioTech): String {
    val codec = audioCodecLabel(track)
    return when {
        track.lossless && track.bits > 0 && track.sampleRate > 0 -> "$codec ${track.bits}/${kiloHertz(track.sampleRate)}"
        track.bitrate > 0 -> "$codec ${track.bitrate / 1000} kb/s"
        else -> codec
    }
}

/** 44100 → "44.1", 96000 → "96". */
private fun kiloHertz(rate: Int): String = if (rate % 1000 == 0) "${rate / 1000}" else "%.1f".format(Locale.ROOT, rate / 1000.0)

/** The short audio word of a card: "Atmos" when the main track has it, its codec otherwise. */
fun audioShortLabel(track: AudioTech): String = if (track.atmos) "Atmos" else audioCodecLabel(track)

/** "TrueHD", "Atmos": the line under a card's title. */
fun techSummary(tech: MediaTech): String = tech.mainAudio()?.let(::audioShortLabel).orEmpty()

/** "7.1", "5.1", "2.0", "1.0"; the channel count when the layout has no such name. */
fun channelLabel(track: AudioTech): String = when {
    track.layout.startsWith("7.1") -> "7.1"
    track.layout.startsWith("5.1") -> "5.1"
    track.layout.startsWith("5.0") -> "5.0"
    track.layout == "stereo" || track.channels == 2 -> "2.0"
    track.layout == "mono" || track.channels == 1 -> "1.0"
    track.channels == 8 -> "7.1"
    track.channels == 6 -> "5.1"
    else -> "${track.channels} ch"
}

/** ISO 639-2 bibliographic codes and the ISO 639-2/T or 639-1 codes the JDK names. */
private val languageCodes = mapOf(
    "tur" to "tr", "eng" to "en", "ger" to "de", "deu" to "de", "fre" to "fr", "fra" to "fr", "spa" to "es", "ita" to "it",
    "jpn" to "ja", "kor" to "ko", "chi" to "zh", "zho" to "zh", "rus" to "ru", "ara" to "ar", "por" to "pt", "dut" to "nl",
    "nld" to "nl", "swe" to "sv", "nor" to "no", "nob" to "nb", "dan" to "da", "fin" to "fi", "pol" to "pl", "cze" to "cs",
    "ces" to "cs", "gre" to "el", "ell" to "el", "heb" to "he", "hin" to "hi", "hun" to "hu", "rum" to "ro", "ron" to "ro",
    "tha" to "th", "ukr" to "uk", "vie" to "vi", "ind" to "id", "per" to "fa", "fas" to "fa", "aze" to "az", "kur" to "ku",
    "bul" to "bg", "hrv" to "hr", "srp" to "sr", "slo" to "sk", "slk" to "sk", "slv" to "sl", "est" to "et", "lav" to "lv",
    "lit" to "lt", "ice" to "is", "isl" to "is", "cat" to "ca", "baq" to "eu", "eus" to "eu", "glg" to "gl", "may" to "ms",
    "msa" to "ms", "tam" to "ta", "tel" to "te", "ben" to "bn", "urd" to "ur", "geo" to "ka", "kat" to "ka", "arm" to "hy",
    "hye" to "hy", "alb" to "sq", "sqi" to "sq", "mac" to "mk", "mkd" to "mk", "bos" to "bs", "fil" to "fil", "tgl" to "tl",
)

/** "Tür", "İng", "Alm" in Turkish; "Tur", "Eng", "Ger" in English; null for untagged tracks. */
fun languageShort(code: String, locale: Locale): String? {
    val tag = code.trim().lowercase(Locale.ROOT)
    if (tag.isEmpty() || tag == "und" || tag == "mis" || tag == "zxx" || tag == "mul") return null
    val language = languageCodes[tag] ?: tag
    val name = Locale(language).getDisplayLanguage(locale)
    if (name.isEmpty() || name.equals(language, ignoreCase = true)) return tag.uppercase(Locale.ROOT).take(3)
    return name.take(3).replaceFirstChar { it.titlecase(locale) }
}

private fun decimal(locale: Locale, value: Double, digits: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(value)

/** "56 Mbps", "4,1 Mbps", "320 kbps". */
fun bitrateLabel(bitsPerSecond: Long, locale: Locale): String = when {
    bitsPerSecond >= 10_000_000 -> decimal(locale, bitsPerSecond / 1e6, 0) + " Mbps"
    bitsPerSecond >= 1_000_000 -> decimal(locale, bitsPerSecond / 1e6, 1) + " Mbps"
    else -> decimal(locale, bitsPerSecond / 1e3, 0) + " kbps"
}

/** "1,8 TB", "58,4 GB", "734 MB". */
fun sizeLabel(bytes: Long, locale: Locale): String = when {
    bytes >= 1_000_000_000_000 -> decimal(locale, bytes / 1e12, 1) + " TB"
    bytes >= 1_000_000_000 -> decimal(locale, bytes / 1e9, 1) + " GB"
    bytes >= 1_000_000 -> decimal(locale, bytes / 1e6, 0) + " MB"
    else -> decimal(locale, bytes / 1e3, 0) + " kB"
}

/** "TrueHD 7.1 Atmos · İng", "AC-3 2.0 · Yorum" ([commentary] is the localized word). */
fun audioLine(track: AudioTech, locale: Locale, commentary: String): String {
    val codec = listOf(audioCodecLabel(track), channelLabel(track)).joinToString(" ") + if (track.atmos) " Atmos" else ""
    return listOfNotNull(codec, languageShort(track.language, locale), commentary.takeIf { track.commentary }).joinToString(" · ")
}

/** "PGS", "SRT", "ASS", "VobSub", "WebVTT", "TX3G" … */
fun subtitleCodecLabel(track: SubtitleTech): String = when (track.codec) {
    "hdmv_pgs_subtitle" -> "PGS"
    "subrip", "srt" -> "SRT"
    "ass", "ssa" -> "ASS"
    "dvd_subtitle" -> "VobSub"
    "dvb_subtitle" -> "DVB"
    "webvtt" -> "WebVTT"
    "mov_text" -> "TX3G"
    else -> track.codec.uppercase(Locale.ROOT)
}

/** "PGS Tür, İng · SRT Tür": the subtitle tracks grouped by format. */
fun subtitleLine(tracks: List<SubtitleTech>, locale: Locale): String =
    tracks.groupBy(::subtitleCodecLabel).entries.joinToString(" · ") { (codec, group) ->
        val languages = group.mapNotNull { languageShort(it.language, locale) }.distinct()
        if (languages.isEmpty()) codec else "$codec ${languages.joinToString(", ")}"
    }
