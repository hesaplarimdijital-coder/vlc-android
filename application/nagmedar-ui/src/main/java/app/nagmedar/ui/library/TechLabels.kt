package app.nagmedar.ui.library

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.Resolution
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import java.text.NumberFormat
import java.util.Locale

/*
 * S-802: the words of the technical badges. Codec and format names are the same in every
 * language; numbers follow the locale ("23,976 fps", "58,4 GB" in Turkish) and languages are
 * shortened from their localized names ("Tür", "İng").
 */

/** Resolution class from a width and height alone (the medialibrary's), null when unknown. */
fun resolutionOf(width: Int, height: Int): Resolution? {
    if (width <= 0 || height <= 0) return null
    val w = maxOf(width, height)
    val h = minOf(width, height)
    return when {
        w >= 7000 || h >= 4000 -> Resolution.UHD8K
        w >= 3200 || h >= 2000 -> Resolution.UHD
        w >= 2400 || h >= 1400 -> Resolution.QHD
        w >= 1800 || h >= 1000 -> Resolution.HD
        w >= 1200 || h >= 700 -> Resolution.HD720
        else -> Resolution.SD
    }
}

/** The corner badge of a card: "4K DV", "4K HDR10+", "4K HDR", "HD", "SD"; null when nothing is known. */
fun techBadge(tech: MediaTech?, width: Int, height: Int): String? {
    val video = tech?.video
    val resolution = video?.resolution ?: resolutionOf(width, height) ?: return null
    val size = when (resolution) {
        Resolution.UHD8K -> "8K"
        Resolution.UHD -> "4K"
        Resolution.QHD -> "1440p"
        Resolution.HD, Resolution.HD720 -> "HD"
        Resolution.SD -> "SD"
    }
    val range = when (video?.dynamicRange) {
        DynamicRange.DolbyVision -> "DV"
        DynamicRange.HDR10Plus -> "HDR10+"
        DynamicRange.HDR10 -> "HDR"
        DynamicRange.HLG -> "HLG"
        else -> null
    }
    return listOfNotNull(size, range).joinToString(" ")
}

/** "DV P8", "DV P7 FEL", "HDR10+", "HDR10", "HLG", "SDR". */
fun rangeLabel(video: VideoTech, withEnhancement: Boolean = false): String = when (video.dynamicRange) {
    DynamicRange.DolbyVision -> "DV P${video.dvProfile}" + if (withEnhancement && video.dvEnhancement.isNotEmpty()) " ${video.dvEnhancement}" else ""
    DynamicRange.HDR10Plus -> "HDR10+"
    DynamicRange.HDR10 -> "HDR10"
    DynamicRange.HLG -> "HLG"
    DynamicRange.SDR -> "SDR"
}

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

/** The short audio word of a card: "Atmos" when the main track has it, its codec otherwise. */
fun audioShortLabel(track: AudioTech): String = if (track.atmos) "Atmos" else audioCodecLabel(track)

/** "2160p · DV P8 · TrueHD": the line under a card's title. */
fun techSummary(tech: MediaTech): String = listOfNotNull(
    tech.video?.resolution?.lines,
    tech.video?.let { rangeLabel(it) },
    tech.mainAudio()?.let(::audioShortLabel),
).joinToString(" · ")

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

/** "HEVC", "AVC", "AV1", "VP9", "MPEG-2" … */
fun videoCodecLabel(video: VideoTech): String = when (video.codec) {
    "hevc" -> "HEVC"
    "h264" -> "AVC"
    "av1" -> "AV1"
    "vp9" -> "VP9"
    "vp8" -> "VP8"
    "mpeg2video" -> "MPEG-2"
    "mpeg4" -> "MPEG-4"
    "vc1" -> "VC-1"
    else -> video.codec.uppercase(Locale.ROOT)
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
    if (name.isEmpty() || name.equals(language, ignoreCase = true)) return tag.uppercase(locale).take(3)
    return name.take(3).replaceFirstChar { it.titlecase(locale) }
}

private fun decimal(locale: Locale, value: Double, digits: Int): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(value)

/** "23,976 fps", "25 fps" (Turkish). */
fun frameRateLabel(fps: Double, locale: Locale): String = decimal(locale, fps, 3) + " fps"

/** "56 Mbps", "4,1 Mbps", "320 kbps". */
fun bitrateLabel(bitsPerSecond: Long, locale: Locale): String = when {
    bitsPerSecond >= 10_000_000 -> decimal(locale, bitsPerSecond / 1e6, 0) + " Mbps"
    bitsPerSecond >= 1_000_000 -> decimal(locale, bitsPerSecond / 1e6, 1) + " Mbps"
    else -> decimal(locale, bitsPerSecond / 1e3, 0) + " kbps"
}

/** "58,4 GB", "734 MB". */
fun sizeLabel(bytes: Long, locale: Locale): String = when {
    bytes >= 1_000_000_000 -> decimal(locale, bytes / 1e9, 1) + " GB"
    bytes >= 1_000_000 -> decimal(locale, bytes / 1e6, 0) + " MB"
    else -> decimal(locale, bytes / 1e3, 0) + " kB"
}

/** "HEVC Main 10 · 3840×2160". */
fun videoLine(video: VideoTech): String =
    listOf(videoCodecLabel(video) + if (video.profile.isNotEmpty()) " ${video.profile}" else "", "${video.width}×${video.height}")
        .joinToString(" · ")

/** "Dolby Vision P7 FEL · HDR10", "HDR10 · 1000 nit", "HLG", "SDR · 8 bit". */
fun rangeLine(video: VideoTech, locale: Locale): String {
    val nits = video.maxLuminance.takeIf { it > 0 }?.let { decimal(locale, it, 0) + " nit" }
    return when (video.dynamicRange) {
        DynamicRange.DolbyVision -> {
            val dv = "Dolby Vision P${video.dvProfile}" + if (video.dvEnhancement.isNotEmpty()) " ${video.dvEnhancement}" else ""
            val base = video.baseLayer.takeIf { it != DynamicRange.DolbyVision }?.let { if (it == DynamicRange.HDR10Plus) "HDR10+" else it.name }
            listOfNotNull(dv, base).joinToString(" · ")
        }
        DynamicRange.HDR10Plus -> listOfNotNull("HDR10+", nits).joinToString(" · ")
        DynamicRange.HDR10 -> listOfNotNull("HDR10", nits).joinToString(" · ")
        DynamicRange.HLG -> "HLG"
        DynamicRange.SDR -> if (video.bitDepth > 0) "SDR · ${video.bitDepth} bit" else "SDR"
    }
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
