package app.nagmedar.ui.player.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.VideoTech
import app.nagmedar.ui.R
import app.nagmedar.ui.library.audioLine
import app.nagmedar.ui.library.bitrateLabel
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.languageShort
import app.nagmedar.ui.library.rangeLine
import app.nagmedar.ui.library.subtitleLine
import app.nagmedar.ui.library.videoLine
import java.text.NumberFormat
import java.util.Locale

/** How a stream's tag is coloured: what plays, a hardware decoder, a software one. */
enum class TagTone { Accent, Good, Warn }

/** One stream of the file as the Info tab lists it (S-605): "V", "A1", "S", what it is, its details, a tag. */
@Immutable
data class StreamRow(
    val mark: String,
    val title: String,
    val meta: String,
    val tag: String? = null,
    val tone: TagTone = TagTone.Accent,
    /** The audio track playing: its row is lit. */
    val current: Boolean = false,
)

/** A label and its value: "Video decoder", "MediaCodec · hardware". */
@Immutable
data class InfoRow(val label: String, val value: String)

/** The file itself: its name and "58,4 GB · Matroska · 2:18:10 · SAMSUNG T7". */
@Immutable
data class FileFacts(val name: String, val details: String)

/** The tracks as libVLC sees them while the film plays, for files the media probe has not read. */
@Immutable
data class PlayerTracks(
    val video: VideoTrackFacts? = null,
    val audio: List<TrackFacts> = emptyList(),
    val subtitles: List<TrackFacts> = emptyList(),
)

@Immutable
data class VideoTrackFacts(val codec: String, val width: Int, val height: Int, val fps: Double)

/** An audio or subtitle track: its codec, language and name, and whether it plays. */
@Immutable
data class TrackFacts(val codec: String, val language: String, val name: String, val selected: Boolean, val channels: Int = 0, val rate: Int = 0)

/** How the video is decoded, as the player's settings ask for it (the decoder in use is the native side's, S-504). */
enum class VideoDecoding(val label: Int, val tag: Int?) {
    Automatic(R.string.nag_info_decoder_auto, R.string.nag_info_tag_hardware),
    Hardware(R.string.nag_info_decoder_hardware, R.string.nag_info_tag_hardware),
    HardwareDirect(R.string.nag_info_decoder_hardware_direct, R.string.nag_info_tag_hardware),
    Software(R.string.nag_info_decoder_software, R.string.nag_info_tag_software),
    /** Hardware decoding failed on this file: VLC went on in software. */
    SoftwareFallback(R.string.nag_info_decoder_fallback, R.string.nag_info_tag_software),
}

/** One second of the player's statistics: the input bitrate and the pictures shown and lost. */
@Immutable
data class StatsSample(val inputMbps: Float, val displayed: Long, val lost: Long)

/**
 * The last minute of statistics, a sample a second (S-605): what the Info tab's graph and the
 * overlay over the video show.
 */
@Stable
class LiveStats(private val capacity: Int = 60) {
    var history by mutableStateOf<List<Float>>(emptyList())
        private set
    var last by mutableStateOf<StatsSample?>(null)
        private set

    fun add(sample: StatsSample?) {
        sample ?: return
        last = sample
        history = (history + sample.inputMbps).takeLast(capacity)
    }

    /** Another film: its own minute. */
    fun clear() {
        history = emptyList()
        last = null
    }
}

/** Whether the statistics show over the video too (S-605: "an overlay or a separate pane"). */
@Stable
class InfoState {
    var overlay by mutableStateOf(false)
}

/** The streams of the playing film: the media probe's (S-802) when it read the file, libVLC's otherwise. */
@Composable
fun streamRows(tech: MediaTech?, tracks: PlayerTracks, decoding: VideoDecoding?): List<StreamRow> {
    val locale = currentLocale()
    val rows = mutableListOf<StreamRow>()
    val decoderTag = decoding?.tag?.let { stringResource(it) }
    val decoderTone = if (decoding == VideoDecoding.Software || decoding == VideoDecoding.SoftwareFallback) TagTone.Warn else TagTone.Good
    val video = tech?.video
    if (video != null) {
        rows += StreamRow(
            mark = stringResource(R.string.nag_info_mark_video),
            title = listOfNotNull(videoLine(video), video.fps.takeIf { it > 0 }?.let { decimal(it, 3, locale) }).joinToString(" · "),
            meta = videoMeta(video, locale),
            tag = decoderTag,
            tone = decoderTone,
        )
    } else {
        tracks.video?.let { v ->
            rows += StreamRow(
                mark = stringResource(R.string.nag_info_mark_video),
                title = listOfNotNull(v.codec.uppercase(Locale.ROOT).takeIf { it.isNotBlank() }, "${v.width}×${v.height}".takeIf { v.width > 0 }, v.fps.takeIf { it > 0 }?.let { decimal(it, 3, locale) })
                    .joinToString(" · "),
                meta = "",
                tag = decoderTag,
                tone = decoderTone,
            )
        }
    }
    // the audio track playing: libVLC's choice, matched to the probe's by its place among the audio tracks
    val playing = tracks.audio.indexOfFirst { it.selected }
    val playingTag = stringResource(R.string.nag_info_tag_playing)
    if (tech != null && tech.audio.isNotEmpty()) {
        val commentary = stringResource(R.string.nag_tech_commentary)
        tech.audio.forEachIndexed { index, track ->
            val details = listOfNotNull(
                track.sampleRate.takeIf { it > 0 }?.let { stringResource(R.string.nag_info_khz, decimal(it / 1000.0, 1, locale)) },
                track.bits.takeIf { it > 0 }?.let { stringResource(R.string.nag_info_bits, it) },
                track.bitrate.takeIf { it > 0 }?.let { "~" + bitrateLabel(it, locale) },
            )
            rows += StreamRow(
                mark = stringResource(R.string.nag_info_mark_audio, index + 1),
                title = audioLine(track, locale, commentary),
                meta = details.joinToString(" · "),
                tag = playingTag.takeIf { index == playing },
                current = index == playing,
            )
        }
    } else {
        tracks.audio.forEachIndexed { index, track ->
            rows += StreamRow(
                mark = stringResource(R.string.nag_info_mark_audio, index + 1),
                title = listOfNotNull(track.codec.uppercase(Locale.ROOT).takeIf { it.isNotBlank() }, languageShort(track.language, locale), track.name.takeIf { it.isNotBlank() })
                    .distinct().joinToString(" · "),
                meta = listOfNotNull(
                    track.channels.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.nag_info_channels, it, it) },
                    track.rate.takeIf { it > 0 }?.let { stringResource(R.string.nag_info_khz, decimal(it / 1000.0, 1, locale)) },
                ).joinToString(" · "),
                tag = playingTag.takeIf { track.selected },
                current = track.selected,
            )
        }
    }
    val subtitleCount = tech?.subtitles?.size?.takeIf { it > 0 } ?: tracks.subtitles.size
    if (subtitleCount > 0) {
        val shown = tracks.subtitles.filter { it.selected }.mapNotNull { languageShort(it.language, locale) ?: it.name.takeIf { name -> name.isNotBlank() } }
        rows += StreamRow(
            mark = stringResource(R.string.nag_info_mark_subtitles),
            title = pluralStringResource(R.plurals.nag_info_subtitle_tracks, subtitleCount, subtitleCount),
            meta = if (tech != null && tech.subtitles.isNotEmpty()) subtitleLine(tech.subtitles, locale) else tracks.subtitles.joinToString(" · ") { it.name },
            tag = shown.takeIf { it.isNotEmpty() }?.joinToString(" + "),
        )
    }
    return rows
}

/** "Dolby Vision P7 FEL · HDR10 · BT.2020 PQ · MaxCLL 1000 · MaxFALL 400": the picture's range and its HDR metadata. */
private fun videoMeta(video: VideoTech, locale: Locale): String = listOfNotNull(
    rangeLine(video, locale),
    colour(video),
    video.maxCll.takeIf { it > 0 }?.let { "MaxCLL $it" },
    video.maxFall.takeIf { it > 0 }?.let { "MaxFALL $it" },
).joinToString(" · ")

/** "BT.2020 PQ", "BT.709": the colour primaries and the transfer, as video tools write them. */
private fun colour(video: VideoTech): String? {
    val primaries = when (video.primaries) {
        "bt2020" -> "BT.2020"
        "bt709" -> "BT.709"
        "smpte432", "smpte431" -> "P3"
        "bt470bg", "smpte170m" -> "BT.601"
        else -> null
    }
    val transfer = when (video.transfer) {
        "smpte2084" -> "PQ"
        "arib-std-b67" -> "HLG"
        else -> null
    }
    return listOfNotNull(primaries, transfer).joinToString(" ").takeIf { it.isNotEmpty() }
}

/** "23,976" in Turkish, "23.976" in English; at most [digits] decimals. */
internal fun decimal(value: Double, digits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(value)

/** The film's report for "Copy the report": the file, its streams, its decoding and the last second. */
fun infoReport(file: FileFacts?, streams: List<StreamRow>, decoding: List<InfoRow>, stats: StatsSample?, labels: ReportLabels): String = buildString {
    file?.let {
        appendLine(it.name)
        if (it.details.isNotEmpty()) appendLine(it.details)
        appendLine()
    }
    if (streams.isNotEmpty()) {
        appendLine(labels.streams)
        streams.forEach { row ->
            append(row.mark).append("  ").append(row.title)
            if (row.meta.isNotEmpty()) append(" · ").append(row.meta)
            if (row.tag != null) append(" [").append(row.tag).append(']')
            appendLine()
        }
        appendLine()
    }
    if (decoding.isNotEmpty()) {
        appendLine(labels.decoding)
        decoding.forEach { appendLine("${it.label}: ${it.value}") }
        appendLine()
    }
    stats?.let {
        appendLine(labels.live)
        appendLine(labels.bitrate)
        appendLine(labels.frames)
    }
}.trimEnd()

/** The report's headings and the last second's lines, in the interface's language. */
class ReportLabels(val streams: String, val decoding: String, val live: String, val bitrate: String, val frames: String)

/** "61,8 Mbps": the input bitrate as the player reads it. */
fun mbpsLabel(mbps: Float, locale: Locale): String = decimal(mbps.toDouble(), 1, locale) + " Mbps"

/** "103 216" in Turkish, "103,216" in English. */
fun countLabel(count: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(count)
