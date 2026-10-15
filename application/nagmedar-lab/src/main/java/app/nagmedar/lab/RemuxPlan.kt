package app.nagmedar.lab

import org.json.JSONArray
import org.json.JSONObject

/** The kinds of stream a plan names, with the letter the engine's references use ("v:0"). */
enum class StreamType(val letter: Char) {
    Video('v'), Audio('a'), Subtitle('s');

    companion object {
        fun of(letter: Char): StreamType? = entries.firstOrNull { it.letter == letter }
    }
}

/**
 * A stream of the source as the probe lists it (S-802): the [ordinal]-th of its [type], cover
 * pictures not counted. Written "v:0", "a:1", "s:0" for the engine.
 */
data class StreamRef(val type: StreamType, val ordinal: Int) {
    override fun toString(): String = "${type.letter}:$ordinal"

    companion object {
        fun parse(text: String): StreamRef? {
            if (text.length < 3 || text[1] != ':') return null
            val type = StreamType.of(text[0]) ?: return null
            val ordinal = text.substring(2).toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return StreamRef(type, ordinal)
        }
    }
}

/** What becomes of the Dolby Vision metadata: kept as it is, or profile 7 converted to 8.1. */
enum class DolbyVisionMode { Keep, ConvertToProfile81 }

/**
 * One stream of the output, in the order the plan lists them (S-1206). The engine copies the
 * source stream's packets untouched; the flags, language and title are the source's unless set
 * here. [encode] is reserved for the compatibility tracks of S-1207 (a codec the source stream is
 * encoded into, beside the original); this story's engine copies only and refuses a plan that
 * asks for more ([RemuxPlan.toNative]).
 */
data class PlannedStream(
    val source: StreamRef,
    /** The probe's codec name ("hevc", "truehd", "subrip"): what the interface shows, not what the engine reads. */
    val codec: String,
    /** How the interface names it, in its language, for the job card and the history (S-208). */
    val label: String = "",
    /** ISO 639-2 tag; null keeps the source's. */
    val language: String? = null,
    /** null keeps the source's; an empty title clears it. */
    val title: String? = null,
    val isDefault: Boolean? = null,
    val forced: Boolean? = null,
    val encode: String? = null,
) {
    val copied: Boolean get() = encode == null
}

/**
 * What a remux writes (S-1206): the streams in order, what happens to the Dolby Vision
 * metadata, and whether chapters, attachments (fonts, covers) and the file's tags come along.
 * Kept as JSON in the job's row ([toJson], [parse]) and handed to the engine as text ([toNative]).
 * The planner of S-1205 edits it; [default] is what the TV Lab queues from a report.
 */
data class RemuxPlan(
    val streams: List<PlannedStream>,
    val dolbyVision: DolbyVisionMode = DolbyVisionMode.Keep,
    val chapters: Boolean = true,
    val attachments: Boolean = true,
    val metadata: Boolean = true,
) {
    val video: PlannedStream? get() = streams.firstOrNull { it.source.type == StreamType.Video }

    /** Whether every stream is a copy: the video is then bit for bit the source's (its base layer, with the conversion). */
    val copiesEverything: Boolean get() = streams.all { it.copied }

    /** The plan as the engine reads it (remux.h); throws when a stream asks for more than a copy. */
    fun toNative(): String {
        require(copiesEverything) { "the engine copies streams only" }
        return buildString {
            append("dv\t").append(if (dolbyVision == DolbyVisionMode.ConvertToProfile81) "convert81" else "keep").append('\n')
            append("chapters\t").append(if (chapters) 1 else 0).append('\n')
            append("attachments\t").append(if (attachments) 1 else 0).append('\n')
            append("metadata\t").append(if (metadata) 1 else 0).append('\n')
            streams.forEach { s ->
                append("stream\t").append(s.source)
                s.isDefault?.let { append("\tdefault=").append(if (it) 1 else 0) }
                s.forced?.let { append("\tforced=").append(if (it) 1 else 0) }
                s.language?.let { append("\tlang=").append(it.clean()) }
                s.title?.let { append("\ttitle=").append(it.clean()) }
                append('\n')
            }
        }
    }

    fun toJson(): String = JSONObject().apply {
        put("dolby_vision", dolbyVision.name)
        put("chapters", chapters)
        put("attachments", attachments)
        put("metadata", metadata)
        put(
            "streams",
            JSONArray().apply {
                streams.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("source", s.source.toString())
                            put("codec", s.codec)
                            put("label", s.label)
                            s.language?.let { put("language", it) }
                            s.title?.let { put("title", it) }
                            s.isDefault?.let { put("default", it) }
                            s.forced?.let { put("forced", it) }
                            s.encode?.let { put("encode", it) }
                        },
                    )
                }
            },
        )
    }.toString()

    companion object {
        /** A tab or a line break would be read as the next field: neither belongs in a tag. */
        private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

        fun parse(json: String): RemuxPlan? = runCatching {
            val o = JSONObject(json)
            val streams = o.getJSONArray("streams").let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    PlannedStream(
                        source = StreamRef.parse(s.getString("source")) ?: error("bad stream reference"),
                        codec = s.optString("codec"),
                        label = s.optString("label"),
                        language = if (s.has("language")) s.getString("language") else null,
                        title = if (s.has("title")) s.getString("title") else null,
                        isDefault = if (s.has("default")) s.getBoolean("default") else null,
                        forced = if (s.has("forced")) s.getBoolean("forced") else null,
                        encode = if (s.has("encode")) s.getString("encode") else null,
                    )
                }
            }
            RemuxPlan(
                streams = streams,
                dolbyVision = DolbyVisionMode.valueOf(o.optString("dolby_vision", DolbyVisionMode.Keep.name)),
                chapters = o.optBoolean("chapters", true),
                attachments = o.optBoolean("attachments", true),
                metadata = o.optBoolean("metadata", true),
            )
        }.getOrNull()

        /**
         * The plan the TV Lab queues from a report: the video and every audio and subtitle track
         * as they are, in the source's order; the Dolby Vision conversion when [convertDolbyVision]
         * asks for it and the film is profile 7 (an HDR10-compatible dual layer). [label] names
         * the tracks as the interface does.
         */
        fun default(tech: MediaTech, convertDolbyVision: Boolean, label: (StreamRef) -> String = { "" }): RemuxPlan {
            val streams = mutableListOf<PlannedStream>()
            tech.video?.let { v ->
                val ref = StreamRef(StreamType.Video, 0)
                streams += PlannedStream(ref, v.codec, label(ref))
            }
            tech.audio.forEachIndexed { i, a ->
                val ref = StreamRef(StreamType.Audio, i)
                streams += PlannedStream(ref, a.codec, label(ref))
            }
            tech.subtitles.forEachIndexed { i, s ->
                val ref = StreamRef(StreamType.Subtitle, i)
                streams += PlannedStream(ref, s.codec, label(ref))
            }
            val convert = convertDolbyVision && tech.video?.dvProfile == 7
            return RemuxPlan(streams, if (convert) DolbyVisionMode.ConvertToProfile81 else DolbyVisionMode.Keep)
        }
    }
}

/** The engine's refusals (remux.h): the output is the source, or already holds something. */
object RemuxRefusal {
    const val SAME_FILE = "same_file"
    const val OUTPUT_NOT_EMPTY = "output_not_empty"
}

/** A stream's count in the engine's stats. */
data class RemuxStreamResult(val source: StreamRef?, val sourceIndex: Int, val outputIndex: Int, val codec: String, val packets: Long, val bytes: Long)

/** What the Dolby Vision conversion did, from the engine's stats. */
data class DolbyVisionResult(
    val inputProfile: Int,
    val inputEnhancement: String,
    val outputProfile: Int,
    val rpusConverted: Long,
    val rpusFailed: Long,
    val enhancementNalsDropped: Long,
    val enhancementBytesDropped: Long,
    val firstRpuError: String,
)

/**
 * The engine's stats of a remux (S-1206), from [NativeLab.remux]'s JSON; [refused] names what
 * kept it from writing at all, see [RemuxRefusal]. [videoHash] is the
 * SHA-256 of the source's video in [hashScope]: "packets" (every packet's bytes), "nal_units"
 * (every H.264/HEVC NAL unit without its framing) or "base_layer_nal_units" (the same without
 * the RPU and the enhancement layer, with the Dolby Vision conversion). The verification
 * (S-1209) hashes the output in the same scope.
 */
data class RemuxResult(
    val error: String,
    /** "same_file" or "output_not_empty" when the engine refused before writing a byte; empty otherwise. */
    val refused: String,
    val cancelled: Boolean,
    val streams: List<RemuxStreamResult>,
    val attachments: Int,
    val chapters: Int,
    val videoCodec: String,
    val videoPackets: Long,
    val videoUnits: Long,
    val hashScope: String,
    val videoHash: String,
    val sourceSeconds: Double,
    val mediaSeconds: Double,
    val bytesIn: Long,
    val bytesOut: Long,
    val wallSeconds: Double,
    val pausedSeconds: Double,
    val peakRssKb: Long,
    val dolbyVision: DolbyVisionResult,
    /** The engine's JSON as it came, kept in the job's row. */
    val json: String = "",
) {
    val ok: Boolean get() = error.isEmpty() && !cancelled

    companion object {
        fun parse(json: String): RemuxResult? = runCatching {
            val o = JSONObject(json)
            val dv = o.optJSONObject("dv") ?: JSONObject()
            RemuxResult(
                error = o.optString("error"),
                refused = o.optString("refused"),
                cancelled = o.optBoolean("cancelled"),
                streams = o.optJSONArray("streams").let { a ->
                    if (a == null) emptyList() else (0 until a.length()).map { i ->
                        val s = a.getJSONObject(i)
                        RemuxStreamResult(StreamRef.parse(s.optString("ref")), s.optInt("source_index"), s.optInt("output_index"), s.optString("codec"), s.optLong("packets"), s.optLong("bytes"))
                    }
                },
                attachments = o.optInt("attachments"),
                chapters = o.optInt("chapters"),
                videoCodec = o.optString("video_codec"),
                videoPackets = o.optLong("video_packets"),
                videoUnits = o.optLong("video_units"),
                hashScope = o.optString("hash_scope"),
                videoHash = o.optString("video_hash"),
                sourceSeconds = o.optDouble("source_seconds", 0.0),
                mediaSeconds = o.optDouble("media_seconds", 0.0),
                bytesIn = o.optLong("bytes_in"),
                bytesOut = o.optLong("bytes_out"),
                wallSeconds = o.optDouble("wall_seconds", 0.0),
                pausedSeconds = o.optDouble("paused_seconds", 0.0),
                peakRssKb = o.optLong("peak_rss_kb"),
                dolbyVision = DolbyVisionResult(
                    inputProfile = dv.optInt("input_profile"),
                    inputEnhancement = dv.optString("input_el_type"),
                    outputProfile = dv.optInt("output_profile"),
                    rpusConverted = dv.optLong("rpus_converted"),
                    rpusFailed = dv.optLong("rpus_failed"),
                    enhancementNalsDropped = dv.optLong("el_nals_dropped"),
                    enhancementBytesDropped = dv.optLong("el_bytes_dropped"),
                    firstRpuError = dv.optString("first_rpu_error"),
                ),
                json = json,
            )
        }.getOrNull()
    }
}

/**
 * The verdict of S-1209, from [NativeLab.remuxVerify]'s JSON: the output's video hashed in
 * [hashScope] against the source's, its packets counted, and with Dolby Vision expected, its
 * configuration record and every RPU checked. [ok] is the whole of it: a mismatch fails the job.
 */
data class RemuxVerification(
    val ok: Boolean,
    val error: String,
    val cancelled: Boolean,
    val hashScope: String,
    val expectedHash: String,
    val hash: String,
    val expectedPackets: Long,
    val packets: Long,
    val units: Long,
    val durationSeconds: Double,
    val dvExpectedProfile: Int,
    val dvProfile: Int,
    val dvCompatibility: Int,
    val dvEnhancementPresent: Boolean,
    val rpus: Long,
    val rpusBad: Long,
    val packetsWithoutRpu: Long,
    /** The verdict's JSON as it came, kept in the job's row. */
    val json: String = "",
) {
    companion object {
        fun parse(json: String): RemuxVerification? = runCatching {
            val o = JSONObject(json)
            RemuxVerification(
                ok = o.optBoolean("ok"),
                error = o.optString("error"),
                cancelled = o.optBoolean("cancelled"),
                hashScope = o.optString("hash_scope"),
                expectedHash = o.optString("expected_hash"),
                hash = o.optString("hash"),
                expectedPackets = o.optLong("expected_packets"),
                packets = o.optLong("packets"),
                units = o.optLong("units"),
                durationSeconds = o.optDouble("duration_seconds", 0.0),
                dvExpectedProfile = o.optInt("dv_expected_profile"),
                dvProfile = o.optInt("dv_profile"),
                dvCompatibility = o.optInt("dv_compatibility"),
                dvEnhancementPresent = o.optInt("dv_el_present") != 0,
                rpus = o.optLong("rpus"),
                rpusBad = o.optLong("rpus_bad"),
                packetsWithoutRpu = o.optLong("packets_without_rpu"),
                json = json,
            )
        }.getOrNull()
    }
}
