package app.nagmedar.lab

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * S-802: what a media file holds, as [NativeLab.probe] reads it with FFmpeg and libdovi: the
 * container, the video stream with its dynamic range and Dolby Vision configuration, every
 * audio and subtitle track. The library caches it (as the JSON) and draws its badges from it.
 */
data class MediaTech(
    /** libavformat's name: "matroska,webm", "mov,mp4,m4a,3gp,3g2,mj2", "mpegts" … */
    val format: String,
    /** MP4 major brand ("isom", "qt  "), empty elsewhere. */
    val brand: String,
    val durationMs: Long,
    val sizeBytes: Long,
    /** Average bit rate of the whole file, bits per second; 0 when unknown. */
    val bitrate: Long,
    val chapters: Int,
    val video: VideoTech?,
    val audio: List<AudioTech>,
    val subtitles: List<SubtitleTech>,
) {
    /** "MKV", "MP4", "MOV", "TS", "AVI" … */
    val container: String
        get() = when {
            format.startsWith("matroska") -> "MKV"
            format.startsWith("mov") -> if (brand.trim() == "qt") "MOV" else "MP4"
            format == "mpegts" -> "TS"
            format.isEmpty() -> ""
            else -> format.substringBefore(',').uppercase()
        }

    /** True when some audio track carries Dolby Atmos (TrueHD or E-AC-3 JOC). */
    val atmos: Boolean get() = audio.any { it.atmos }
}

/** Dynamic range of a video stream; Dolby Vision may also carry an HDR10 or HLG base layer. */
enum class DynamicRange { SDR, HLG, HDR10, HDR10Plus, DolbyVision }

/** Resolution classes as the library groups them. */
enum class Resolution(val lines: String) {
    SD("480p"), HD720("720p"), HD("1080p"), QHD("1440p"), UHD("2160p"), UHD8K("4320p");

    val isUhd: Boolean get() = this == UHD || this == UHD8K
}

data class VideoTech(
    /** FFmpeg codec name: "hevc", "h264", "av1", "vp9", "mpeg2video" … */
    val codec: String,
    /** "Main 10", "High" …; empty when unknown. */
    val profile: String,
    val width: Int,
    val height: Int,
    val fps: Double,
    val bitDepth: Int,
    /** FFmpeg transfer characteristics: "smpte2084" (PQ), "arib-std-b67" (HLG), "bt709", "unknown" … */
    val transfer: String,
    val primaries: String,
    val matrix: String,
    /** Mastering display peak and black level, cd/m²; 0 when not signalled. */
    val maxLuminance: Double,
    val minLuminance: Double,
    val maxCll: Int,
    val maxFall: Int,
    val hdr10Plus: Boolean,
    /** Dolby Vision profile (5, 7, 8, 10 …), 0 without Dolby Vision. */
    val dvProfile: Int,
    val dvLevel: Int,
    /** Base layer signal compatibility: 1 HDR10, 2 SDR, 4 HLG, 6 HDR10 (profile 7), 0 none (profile 5). */
    val dvCompatibility: Int,
    /** "FEL" or "MEL" for profile 7, empty otherwise. */
    val dvEnhancement: String,
    val dvEnhancementLayer: Boolean,
    val dvRpu: Boolean,
) {
    private val pq: Boolean
        get() = transfer == "smpte2084" || (maxLuminance > 0 && transfer in setOf("", "unknown", "reserved"))

    val dynamicRange: DynamicRange
        get() = when {
            dvProfile > 0 -> DynamicRange.DolbyVision
            hdr10Plus -> DynamicRange.HDR10Plus
            pq -> DynamicRange.HDR10
            transfer == "arib-std-b67" -> DynamicRange.HLG
            else -> DynamicRange.SDR
        }

    /** What a player without Dolby Vision shows: the base layer's range (profile 5 has none it can show). */
    val baseLayer: DynamicRange
        get() = when {
            dvProfile == 0 -> dynamicRange
            dvCompatibility == 1 || dvCompatibility == 6 -> if (hdr10Plus) DynamicRange.HDR10Plus else DynamicRange.HDR10
            dvCompatibility == 4 -> DynamicRange.HLG
            dvCompatibility == 2 -> DynamicRange.SDR
            else -> DynamicRange.DolbyVision
        }

    /** By the larger side, so cropped scope films (3840 × 1600) are still 4K. */
    val resolution: Resolution
        get() = when {
            width >= 7000 || height >= 4000 -> Resolution.UHD8K
            width >= 3200 || height >= 2000 -> Resolution.UHD
            width >= 2400 || height >= 1400 -> Resolution.QHD
            width >= 1800 || height >= 1000 -> Resolution.HD
            width >= 1200 || height >= 700 -> Resolution.HD720
            else -> Resolution.SD
        }
}

data class AudioTech(
    /** FFmpeg codec name: "truehd", "eac3", "ac3", "dts", "aac", "flac", "pcm_s24le" … */
    val codec: String,
    /** "Dolby TrueHD + Dolby Atmos", "DTS-HD MA + DTS:X", "DTS-HD MA" …; empty when FFmpeg has none. */
    val profile: String,
    val channels: Int,
    val layout: String,
    val sampleRate: Int,
    val bits: Int,
    val bitrate: Long,
    /** ISO 639-2 code as tagged ("tur", "eng"), empty when untagged. */
    val language: String,
    val title: String,
    val isDefault: Boolean,
    val forced: Boolean,
    val commentary: Boolean,
) {
    val atmos: Boolean get() = profile.contains("Atmos")
    val dtsX: Boolean get() = profile.contains("DTS:X")
    val lossless: Boolean
        get() = codec in setOf("truehd", "mlp", "flac", "alac", "ape", "wavpack") || codec.startsWith("pcm_") ||
            profile.startsWith("DTS-HD MA")
}

data class SubtitleTech(
    /** "subrip", "ass", "hdmv_pgs_subtitle", "dvd_subtitle", "mov_text", "webvtt" … */
    val codec: String,
    val language: String,
    val title: String,
    val isDefault: Boolean,
    val forced: Boolean,
    val hearingImpaired: Boolean,
) {
    /** Picture subtitles (PGS, VobSub, DVB) cannot be restyled or searched. */
    val bitmap: Boolean get() = codec in setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub")
}

object MediaProbe {
    /** The native JSON version (MEDIA_PROBE_VERSION in mediaprobe.h); older cache rows are probed again. */
    const val VERSION = 1

    /** The probe's JSON as [MediaTech]; null when the file could not be read or the JSON is not a probe. */
    fun parse(json: String): MediaTech? = runCatching {
        val o = JSONObject(json)
        if (o.optString("error").isNotEmpty() || o.optInt("version") != VERSION) return null
        MediaTech(
            format = o.optString("format"),
            brand = o.optString("brand"),
            durationMs = o.optLong("duration_ms"),
            sizeBytes = o.optLong("size"),
            bitrate = o.optLong("bitrate"),
            chapters = o.optInt("chapters"),
            video = o.optJSONObject("video")?.let(::video),
            audio = o.optJSONArray("audio").objects().map(::audio),
            subtitles = o.optJSONArray("subtitles").objects().map(::subtitle),
        )
    }.getOrNull()

    /**
     * Probes [uri] (file:// or content://); the JSON, or null without the native library or when
     * the file cannot be opened. Blocking: call it on a background thread.
     */
    fun probe(context: Context, uri: Uri): String? {
        if (!NativeLab.available) return null
        return runCatching {
            when (uri.scheme) {
                null, "file" -> uri.path?.takeIf { File(it).canRead() }?.let(NativeLab::probe)
                "content" -> context.contentResolver.openFileDescriptor(uri, "r")?.use { NativeLab.probe("/proc/self/fd/${it.fd}") }
                else -> null
            }
        }.getOrNull()
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun video(v: JSONObject) = VideoTech(
        codec = v.optString("codec"),
        profile = v.optString("profile"),
        width = v.optInt("width"),
        height = v.optInt("height"),
        fps = v.optDouble("fps", 0.0),
        bitDepth = v.optInt("bit_depth"),
        transfer = v.optString("transfer"),
        primaries = v.optString("primaries"),
        matrix = v.optString("matrix"),
        maxLuminance = v.optDouble("max_luminance", 0.0),
        minLuminance = v.optDouble("min_luminance", 0.0),
        maxCll = v.optInt("max_cll"),
        maxFall = v.optInt("max_fall"),
        hdr10Plus = v.optBoolean("hdr10plus"),
        dvProfile = v.optInt("dv_profile"),
        dvLevel = v.optInt("dv_level"),
        dvCompatibility = v.optInt("dv_compatibility"),
        dvEnhancement = v.optString("dv_el"),
        dvEnhancementLayer = v.optBoolean("dv_el_present"),
        dvRpu = v.optBoolean("dv_rpu"),
    )

    private fun audio(a: JSONObject) = AudioTech(
        codec = a.optString("codec"),
        profile = a.optString("profile"),
        channels = a.optInt("channels"),
        layout = a.optString("layout"),
        sampleRate = a.optInt("sample_rate"),
        bits = a.optInt("bits"),
        bitrate = a.optLong("bitrate"),
        language = a.optString("language"),
        title = a.optString("title"),
        isDefault = a.optBoolean("default"),
        forced = a.optBoolean("forced"),
        commentary = a.optBoolean("commentary"),
    )

    private fun subtitle(s: JSONObject) = SubtitleTech(
        codec = s.optString("codec"),
        language = s.optString("language"),
        title = s.optString("title"),
        isDefault = s.optBoolean("default"),
        forced = s.optBoolean("forced"),
        hearingImpaired = s.optBoolean("hearing_impaired"),
    )
}
