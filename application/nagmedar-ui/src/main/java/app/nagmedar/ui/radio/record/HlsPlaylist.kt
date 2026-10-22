package app.nagmedar.ui.radio.record

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** A byte range of a resource, as `EXT-X-BYTERANGE` and `EXT-X-MAP` give it. */
data class HlsByteRange(val offset: Long, val length: Long)

/** A file every segment after it needs (`EXT-X-MAP`): fragmented MP4's initialisation. */
data class HlsMap(val url: String, val range: HlsByteRange? = null)

/** One segment of a media playlist: [sequence] is its media sequence number, [seconds] how long it plays. */
data class HlsSegment(
    val url: String,
    val seconds: Double,
    val sequence: Long,
    val range: HlsByteRange? = null,
    val map: HlsMap? = null,
    /** A break in the stream comes before it (`EXT-X-DISCONTINUITY`). */
    val discontinuity: Boolean = false,
    /** It is encrypted (`EXT-X-KEY` with a method other than NONE). */
    val encrypted: Boolean = false,
)

/** A variant of a master playlist. [video] is set when its codecs or resolution say it carries pictures. */
data class HlsVariant(val url: String, val bandwidth: Long, val video: Boolean, val audioGroup: String?)

/** An alternative audio rendition (`EXT-X-MEDIA:TYPE=AUDIO`) with a playlist of its own. */
data class HlsRendition(val url: String, val group: String, val default: Boolean)

/** What [HlsMaster.choose] picked: the media playlist's address, and whether it is sound only. */
data class HlsChoice(val url: String, val audioOnly: Boolean)

/** S-1107: a parsed HLS playlist. */
sealed interface HlsPlaylist

/** A master playlist: the variants to choose from. */
class HlsMaster(val variants: List<HlsVariant>, val renditions: List<HlsRendition>) : HlsPlaylist {
    /**
     * The playlist to record: of the variants with sound only, the one with the most bandwidth; else the
     * audio rendition a video variant points to (the default one); else the variant with the least
     * bandwidth (its pictures come along: the file is a transport stream or an MP4). Null: nothing to take.
     */
    fun choose(): HlsChoice? {
        val sound = variants.filter { !it.video }.maxByOrNull { it.bandwidth }
        if (sound != null) return HlsChoice(sound.url, audioOnly = true)
        for (variant in variants.sortedBy { it.bandwidth }) {
            val group = variant.audioGroup ?: continue
            val inGroup = renditions.filter { it.group == group }
            val pick = inGroup.firstOrNull { it.default } ?: inGroup.firstOrNull() ?: continue
            return HlsChoice(pick.url, audioOnly = true)
        }
        val least = variants.minByOrNull { it.bandwidth }
        if (least != null) return HlsChoice(least.url, audioOnly = false)
        return renditions.firstOrNull()?.let { HlsChoice(it.url, audioOnly = true) }
    }
}

/** A media playlist: the segments now listed, numbered from [mediaSequence]. [ended] is set once the stream is over (`EXT-X-ENDLIST`). */
class HlsMedia(
    val targetSeconds: Int,
    val mediaSequence: Long,
    val segments: List<HlsSegment>,
    val ended: Boolean,
    /** Only I-frames (a trick-play playlist): no sound. */
    val keyFramesOnly: Boolean = false,
) : HlsPlaylist {
    /** The media sequence number of the last segment listed; one below [mediaSequence] when none is. */
    val lastSequence: Long get() = mediaSequence + segments.size - 1
}

/** S-1107: reads HLS playlists (RFC 8216) as far as recording them needs. */
object HlsPlaylists {

    /** Whether [text] is an HLS playlist (an m3u that has HLS tags), not a plain list of stations. */
    fun isHls(text: String): Boolean = text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("#EXTM3U") && text.contains("#EXT-X-")

    /** The playlist in [text], whose relative addresses are made whole against [base]; null when it is none. */
    fun parse(text: String, base: String): HlsPlaylist? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty() || !lines[0].trimStart('﻿').startsWith("#EXTM3U")) return null
        return if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) master(lines, base) else media(lines, base)
    }

    private fun master(lines: List<String>, base: String): HlsMaster {
        val variants = ArrayList<HlsVariant>()
        val renditions = ArrayList<HlsRendition>()
        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pending = attributes(line.substringAfter(':'))
                line.startsWith("#EXT-X-MEDIA:") -> {
                    val a = attributes(line.substringAfter(':'))
                    val uri = a["URI"] ?: continue
                    if (a["TYPE"].equals("AUDIO", ignoreCase = true)) {
                        resolve(base, uri)?.let { renditions += HlsRendition(it, a["GROUP-ID"].orEmpty(), a["DEFAULT"].equals("YES", ignoreCase = true)) }
                    }
                }
                line.startsWith("#") -> Unit
                pending != null -> {
                    val a = pending
                    pending = null
                    val url = resolve(base, line) ?: continue
                    val codecs = a["CODECS"]?.lowercase(Locale.ROOT)
                    val video = a.containsKey("RESOLUTION") || (codecs != null && VIDEO_CODECS.any { codecs.contains(it) })
                    variants += HlsVariant(url, a["BANDWIDTH"]?.toLongOrNull() ?: a["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L, video, a["AUDIO"])
                }
            }
        }
        return HlsMaster(variants, renditions)
    }

    private fun media(lines: List<String>, base: String): HlsMedia {
        var target = 0
        var sequence = 0L
        var ended = false
        var keyFramesOnly = false
        var duration = -1.0
        var discontinuity = false
        var encrypted = false
        var map: HlsMap? = null
        var range: HlsByteRange? = null
        // where a byte range without an offset begins: after the last one of the same resource
        var nextOffset = 0L
        val segments = ArrayList<HlsSegment>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').trim().toIntOrNull() ?: target
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: sequence
                line == "#EXT-X-ENDLIST" -> ended = true
                line == "#EXT-X-I-FRAMES-ONLY" -> keyFramesOnly = true
                line == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                line.startsWith("#EXT-X-KEY:") -> encrypted = !attributes(line.substringAfter(':'))["METHOD"].equals("NONE", ignoreCase = true)
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attributes(line.substringAfter(':'))
                    map = a["URI"]?.let { uri -> resolve(base, uri)?.let { HlsMap(it, a["BYTERANGE"]?.let(::byteRange)) } }
                }
                line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val parts = line.substringAfter(':').trim().split('@')
                    val length = parts[0].toLongOrNull()
                    if (length != null) {
                        val offset = parts.getOrNull(1)?.toLongOrNull() ?: nextOffset
                        range = HlsByteRange(offset, length)
                        nextOffset = offset + length
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolve(base, line)
                    if (url != null && duration >= 0) {
                        segments += HlsSegment(url, duration, sequence + segments.size, range, map, discontinuity, encrypted)
                    }
                    duration = -1.0
                    discontinuity = false
                    range = null
                }
            }
        }
        return HlsMedia(target, sequence, segments, ended, keyFramesOnly)
    }

    private fun byteRange(text: String): HlsByteRange? {
        val parts = text.split('@')
        val length = parts[0].toLongOrNull() ?: return null
        return HlsByteRange(parts.getOrNull(1)?.toLongOrNull() ?: 0L, length)
    }

    /** `KEY=value,KEY="a,b"` as a map; the quotes are taken off. */
    internal fun attributes(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < text.length) {
            val eq = text.indexOf('=', i)
            if (eq < 0) break
            val key = text.substring(i, eq).trim().uppercase(Locale.ROOT)
            var j = eq + 1
            val value: String
            if (j < text.length && text[j] == '"') {
                val close = text.indexOf('"', j + 1).let { if (it < 0) text.length else it }
                value = text.substring(j + 1, close)
                j = close + 1
            } else {
                val comma = text.indexOf(',', j).let { if (it < 0) text.length else it }
                value = text.substring(j, comma).trim()
                j = comma
            }
            out[key] = value
            i = text.indexOf(',', j).let { if (it < 0) text.length else it + 1 }
        }
        return out
    }

    /** [reference] against [base]; null when it makes no HTTP address. */
    internal fun resolve(base: String, reference: String): String? {
        val ref = reference.trim()
        if (ref.isEmpty()) return null
        val resolved = base.toHttpUrlOrNull()?.resolve(ref)?.toString()
        if (resolved != null) return resolved
        return ref.toHttpUrlOrNull()?.toString()
    }

    private val VIDEO_CODECS = listOf("avc1", "avc3", "hvc1", "hev1", "dvh1", "dvhe", "vp09", "vp08", "av01", "mp4v")
}
