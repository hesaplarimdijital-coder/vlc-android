package app.nagmedar.lab

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * S-802: what a media file holds, as [NativeLab.probe] reads it with FFmpeg: the container and
 * every audio and subtitle track (Nağmedar 1.0 plays sound only: a film's picture is left out).
 * The library caches it (as the JSON) and lists its technical facts from it.
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
