package app.nagmedar.ui.tvlab

import androidx.compose.runtime.Immutable
import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.SubtitleTech
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** The HDR formats a screen and its player show as HDR. */
enum class HdrFormat(val key: String, val label: String) {
    HDR10("hdr10", "HDR10"),
    HDR10Plus("hdr10plus", "HDR10+"),
    HLG("hlg", "HLG"),
    DolbyVision("dv", "Dolby Vision"),
    ;

    companion object {
        fun of(key: String): HdrFormat? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the device gets the file: a USB 2.0 port (a TV's, about 30 MB/s in practice), a USB 3 port,
 * the network, or its own storage (the tablet playing it itself). [kbps] is what the link
 * carries at most; null when it does not limit a film.
 */
enum class SourceLink(val key: String, val kbps: Int?) {
    Usb2("usb2", 240_000),
    Usb3("usb3", null),
    Network("network", null),
    Local("local", null),
    ;

    companion object {
        fun of(key: String?): SourceLink = entries.firstOrNull { it.key == key } ?: Usb2
    }
}

/**
 * What a target device plays (S-1203): its containers, video codecs and limits, HDR formats and
 * Dolby Vision profiles, audio and subtitle codecs, and the highest second of bitrate it takes
 * ([peakKbps], 0 for no limit) from its [source]. Codecs are FFmpeg's names as the media probe
 * gives them ("hevc", "eac3"), audio and subtitles in the keys of [audioKey] and [subtitleKey];
 * "*" in a set allows everything. [nagmedar]: the device plays with Nağmedar itself, which
 * decodes any audio, reads any subtitle's character set and reshapes Dolby Vision profile 5.
 * Kept as JSON ([DeviceProfileCodec]), editable and shareable.
 */
@Immutable
data class DeviceProfile(
    val id: String,
    val name: String,
    /** "webOS 24 · USB". */
    val detail: String,
    val source: SourceLink = SourceLink.Usb2,
    /** The highest second it takes, in kbps; 0 without a limit. */
    val peakKbps: Int = 0,
    /** "MKV", "MP4", "TS", as [app.nagmedar.lab.MediaTech.container] names them. */
    val containers: Set<String> = setOf(ANY),
    val videoCodecs: Set<String> = setOf(ANY),
    val maxWidth: Int = 3840,
    val maxHeight: Int = 2160,
    val maxFps: Double = 120.0,
    val maxBitDepth: Int = 10,
    val hdr: Set<HdrFormat> = setOf(HdrFormat.HDR10, HdrFormat.HLG),
    /** Dolby Vision profiles it plays as Dolby Vision (with [HdrFormat.DolbyVision]). */
    val dvProfiles: Set<Int> = emptySet(),
    val audioCodecs: Set<String> = setOf(ANY),
    val subtitleCodecs: Set<String> = setOf(ANY),
    val nagmedar: Boolean = false,
    /** Whether it is one of the app's own profiles (it can go back to them). */
    val builtIn: Boolean = false,
) {
    fun allowsContainer(container: String) = ANY in containers || containers.any { it.equals(container, ignoreCase = true) }

    fun allowsVideo(codec: String) = ANY in videoCodecs || codec.lowercase(Locale.ROOT) in videoCodecs

    fun allowsAudio(key: String) = ANY in audioCodecs || key in audioCodecs

    fun allowsSubtitle(key: String) = ANY in subtitleCodecs || key in subtitleCodecs

    /** The bitrate it can take at most: its own limit and its link's, the lower; 0 for none. */
    val limitKbps: Int
        get() = listOfNotNull(peakKbps.takeIf { it > 0 }, source.kbps).minOrNull() ?: 0

    companion object {
        const val ANY = "*"
    }
}

/**
 * An audio track's codec in a profile's words: "truehd", "eac3", "ac3", "ac4", "dts" (the DTS
 * core), "dtshd" (HD MA and HRA, which carry the core), "aac", "mp3", "pcm", "flac", "opus" …
 */
fun audioKey(track: AudioTech): String = when {
    track.codec == "dts" && (track.profile.startsWith("DTS-HD") || track.dtsX) -> "dtshd"
    track.codec == "dts" -> "dts"
    track.codec.startsWith("pcm_") -> "pcm"
    track.codec == "aac_latm" -> "aac"
    track.codec == "mp3float" -> "mp3"
    else -> track.codec.lowercase(Locale.ROOT)
}

/** A subtitle track's codec in a profile's words: "srt", "ass", "pgs", "vobsub", "dvb", "webvtt", "tx3g" … */
fun subtitleKey(track: SubtitleTech): String = when (track.codec) {
    "subrip", "srt" -> "srt"
    "ass", "ssa" -> "ass"
    "hdmv_pgs_subtitle" -> "pgs"
    "dvd_subtitle" -> "vobsub"
    "dvb_subtitle" -> "dvb"
    "webvtt" -> "webvtt"
    "mov_text" -> "tx3g"
    "sami" -> "smi"
    else -> track.codec.lowercase(Locale.ROOT)
}

/** The app's profiles: the TV the plan aims at, and the tablet playing films itself. */
object BuiltInProfiles {
    /**
     * LG OLED B4 (webOS 24), its own player from a USB drive: HDR10, HLG and Dolby Vision
     * (profiles 5 and 8; no HDR10+, no dual-layer profile 7); LG's audio list (AC-4, AC-3,
     * E-AC-3, AAC, MP3, PCM; no DTS, no TrueHD); text subtitles; USB 2.0 ports.
     */
    val lgB4Usb = DeviceProfile(
        id = "lg-oled-b4-usb",
        name = "LG OLED B4",
        detail = "webOS 24 · USB",
        source = SourceLink.Usb2,
        peakKbps = 100_000,
        containers = setOf("MKV", "MP4", "TS", "MOV", "AVI"),
        videoCodecs = setOf("hevc", "h264", "av1", "vp9", "mpeg2video", "mpeg4"),
        maxWidth = 3840,
        maxHeight = 2160,
        maxFps = 120.0,
        maxBitDepth = 10,
        hdr = setOf(HdrFormat.HDR10, HdrFormat.HLG, HdrFormat.DolbyVision),
        dvProfiles = setOf(5, 8),
        audioCodecs = setOf("ac4", "ac3", "eac3", "aac", "mp3", "pcm"),
        subtitleCodecs = setOf("srt", "ass", "smi", "webvtt"),
        builtIn = true,
    )

    /**
     * Galaxy Tab S11 Ultra playing with Nağmedar, on its screen or over HDMI: HDR10, HDR10+ and
     * HLG (no Dolby Vision on the device; profile 5 is reshaped in software, the others show their
     * base layer); every audio and subtitle codec through libVLC; its own storage.
     */
    val tabS11Ultra = DeviceProfile(
        id = "tab-s11-ultra",
        name = "Tab S11 Ultra",
        detail = "One UI 8 · HDMI",
        source = SourceLink.Local,
        peakKbps = 0,
        containers = setOf(DeviceProfile.ANY),
        videoCodecs = setOf("hevc", "h264", "av1", "vp9", "vp8", "mpeg2video", "mpeg4", "vc1"),
        maxWidth = 7680,
        maxHeight = 4320,
        maxFps = 120.0,
        maxBitDepth = 10,
        hdr = setOf(HdrFormat.HDR10, HdrFormat.HDR10Plus, HdrFormat.HLG),
        dvProfiles = emptySet(),
        audioCodecs = setOf(DeviceProfile.ANY),
        subtitleCodecs = setOf(DeviceProfile.ANY),
        nagmedar = true,
        builtIn = true,
    )

    val all: List<DeviceProfile> = listOf(lgB4Usb, tabS11Ultra)

    fun of(id: String): DeviceProfile? = all.firstOrNull { it.id == id }
}

/**
 * A profile as a JSON file (S-1203), schema 1:
 *
 *   {"schema":1,"id":"lg-oled-b4-usb","name":"LG OLED B4","detail":"webOS 24 · USB",
 *    "source":"usb2","peak_mbps":100,"nagmedar":false,"containers":["MKV","MP4"],
 *    "video":{"codecs":["hevc","h264"],"max_width":3840,"max_height":2160,"max_fps":120,
 *             "max_bit_depth":10,"hdr":["hdr10","hlg","dv"],"dv_profiles":[5,8]},
 *    "audio":{"codecs":["ac3","eac3"]},"subtitles":{"codecs":["srt","ass"]}}
 *
 * Unknown keys are left, missing ones read as no limit, so a hand-written file stays readable.
 */
object DeviceProfileCodec {
    const val SCHEMA = 1

    fun encode(profile: DeviceProfile): String = JSONObject()
        .put("schema", SCHEMA)
        .put("id", profile.id)
        .put("name", profile.name)
        .put("detail", profile.detail)
        .put("source", profile.source.key)
        .put("peak_mbps", profile.peakKbps / 1000.0)
        .put("nagmedar", profile.nagmedar)
        .put("containers", JSONArray(profile.containers.sorted()))
        .put(
            "video",
            JSONObject()
                .put("codecs", JSONArray(profile.videoCodecs.sorted()))
                .put("max_width", profile.maxWidth)
                .put("max_height", profile.maxHeight)
                .put("max_fps", profile.maxFps)
                .put("max_bit_depth", profile.maxBitDepth)
                .put("hdr", JSONArray(HdrFormat.entries.filter { it in profile.hdr }.map { it.key }))
                .put("dv_profiles", JSONArray(profile.dvProfiles.sorted())),
        )
        .put("audio", JSONObject().put("codecs", JSONArray(profile.audioCodecs.sorted())))
        .put("subtitles", JSONObject().put("codecs", JSONArray(profile.subtitleCodecs.sorted())))
        .toString(2)

    /** The profile in [text]; null when it is not a profile this app reads. */
    fun decode(text: String, builtIn: Boolean = false): DeviceProfile? = runCatching {
        val o = JSONObject(text)
        val schema = o.optInt("schema", SCHEMA)
        if (schema > SCHEMA) return null
        val id = o.optString("id").trim().ifEmpty { return null }
        val video = o.optJSONObject("video") ?: JSONObject()
        DeviceProfile(
            id = id,
            name = o.optString("name").trim().ifEmpty { id },
            detail = o.optString("detail").trim(),
            source = SourceLink.of(o.optString("source")),
            peakKbps = (o.optDouble("peak_mbps", 0.0).takeIf { !it.isNaN() } ?: 0.0).times(1000).toInt().coerceAtLeast(0),
            nagmedar = o.optBoolean("nagmedar", false),
            containers = o.optJSONArray("containers").strings(upper = true).ifEmpty { setOf(DeviceProfile.ANY) },
            videoCodecs = video.optJSONArray("codecs").strings().ifEmpty { setOf(DeviceProfile.ANY) },
            maxWidth = video.optInt("max_width", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE,
            maxHeight = video.optInt("max_height", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE,
            maxFps = video.optDouble("max_fps", Double.MAX_VALUE).takeIf { !it.isNaN() && it > 0 } ?: Double.MAX_VALUE,
            maxBitDepth = video.optInt("max_bit_depth", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE,
            hdr = video.optJSONArray("hdr").strings().mapNotNull(HdrFormat::of).toSet(),
            dvProfiles = video.optJSONArray("dv_profiles").let { a -> if (a == null) emptySet() else (0 until a.length()).map { a.optInt(it) }.filter { it > 0 }.toSet() },
            audioCodecs = o.optJSONObject("audio")?.optJSONArray("codecs").strings().ifEmpty { setOf(DeviceProfile.ANY) },
            subtitleCodecs = o.optJSONObject("subtitles")?.optJSONArray("codecs").strings().ifEmpty { setOf(DeviceProfile.ANY) },
            builtIn = builtIn,
        )
    }.getOrNull()

    private fun JSONArray?.strings(upper: Boolean = false): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotEmpty() } }
            .map { if (upper) it.uppercase(Locale.ROOT) else if (it == DeviceProfile.ANY) it else it.lowercase(Locale.ROOT) }
            .toSet()
    }
}
