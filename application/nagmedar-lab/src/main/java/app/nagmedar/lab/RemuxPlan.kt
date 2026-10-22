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

/** S-1207: the codecs a compatibility track is encoded into, by the engine's names (remux.h). */
enum class EncodeCodec(val native: String, val label: String, val lossless: Boolean) {
    /** E-AC-3 at 48 kHz, at most 5.1: what the TVs that play no TrueHD or DTS play. */
    EAC3("eac3", "E-AC-3", false),
    /** AC-3, at most 640 kbps: for a device older than E-AC-3. */
    AC3("ac3", "AC-3", false),
    /** LPCM in Matroska, 24 bits (16 for a 16-bit source), the source's rate, up to 7.1. */
    LPCM("lpcm", "LPCM", true),
    ;

    companion object {
        fun of(native: String): EncodeCodec? = entries.firstOrNull { it.native == native }
    }
}

/**
 * S-1207: how a compatibility track is made from its source: [codec], at [bitrateKbps] (E-AC-3
 * and AC-3; null for the engine's own: 640 for 5.1, 256 for stereo), with at most [channels]
 * (null keeps the source's as far as the codec goes: 7.1 becomes 5.1 in E-AC-3; 2 for a stereo
 * downmix), and made the default audio track when [makeDefault] (every other audio track then
 * loses the flag, so that the device starts with it).
 */
data class EncodeSpec(
    val codec: EncodeCodec,
    val bitrateKbps: Int? = null,
    val channels: Int? = null,
    val makeDefault: Boolean = false,
) {
    /** "E-AC-3 5.1", "LPCM 7.1": the track as the engine writes it from [source], for its title. */
    fun describe(source: AudioTech): String = "${codec.label} ${channelsOf(source)}"

    /** "5.1", "2.0": the channels the engine gives the track (remux.c target_layout). */
    fun channelsOf(source: AudioTech): String {
        val n = minOf(channels ?: source.channels, source.channels).coerceAtLeast(1)
        if (codec == EncodeCodec.LPCM) {
            // the WAV order Matroska's PCM means: FFmpeg's default layout of that many channels
            return LPCM_LAYOUTS.getOrElse(minOf(n, 8) - 1) { "2.0" }
        }
        val e = minOf(n, 6)
        if (e <= 2) return "$e.0"
        val lfe = source.layout.contains(".1") || source.layout.contains("LFE", ignoreCase = true)
        return when {
            // the source's own layout, which E-AC-3 takes up to 5.1
            e == source.channels && e < 6 -> if (lfe) "${e - 1}.1" else "$e.0"
            // a downmix (or a six-channel layout E-AC-3 has not): 5.1, or 5.0 without an LFE to keep
            lfe || e >= 6 -> "5.1"
            else -> "5.0"
        }
    }

    /** Whether the added track holds the source's samples as decoded: LPCM with every channel. */
    val lossless: Boolean get() = codec.lossless && channels == null

    private companion object {
        val LPCM_LAYOUTS = listOf("1.0", "2.0", "2.1", "4.0", "5.0", "5.1", "6.1", "7.1")
    }
}

/** What a planned stream is to the listener: the original copied untouched, or a track added beside it, lossy or not. */
enum class StreamFidelity { Kept, AddedLossy, AddedLossless }

/**
 * One stream of the output, in the order the plan lists them (S-1206). The engine copies the
 * source stream's packets untouched; the flags, language and title are the source's unless set
 * here. With [encode] (S-1207) the entry is a compatibility track instead: the source audio
 * stream decoded and encoded again, written where the entry stands (right after the original,
 * which stays its own entry); its [isDefault] is [EncodeSpec.makeDefault]'s.
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
    val encode: EncodeSpec? = null,
) {
    val copied: Boolean get() = encode == null

    /** The plan's lossless label: an original stays lossless, an added track is what its codec makes it. */
    val fidelity: StreamFidelity
        get() = when {
            encode == null -> StreamFidelity.Kept
            encode.lossless -> StreamFidelity.AddedLossless
            else -> StreamFidelity.AddedLossy
        }
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

    /** Whether every stream is a copy, no compatibility track added (the video is bit for bit the source's either way). */
    val copiesEverything: Boolean get() = streams.all { it.copied }

    /** The compatibility tracks the plan adds (S-1207). */
    val added: List<PlannedStream> get() = streams.filter { !it.copied }

    /** The plan as the engine reads it (remux.h): a stream line per copy, an encode line per added track. */
    fun toNative(): String {
        // the first added track asked to be default is; every other audio track then is not
        val defaultTrack = streams.firstOrNull { it.encode?.makeDefault == true }
        return buildString {
            append("dv\t").append(if (dolbyVision == DolbyVisionMode.ConvertToProfile81) "convert81" else "keep").append('\n')
            append("chapters\t").append(if (chapters) 1 else 0).append('\n')
            append("attachments\t").append(if (attachments) 1 else 0).append('\n')
            append("metadata\t").append(if (metadata) 1 else 0).append('\n')
            streams.forEach { s ->
                val encode = s.encode
                val isDefault = when {
                    encode != null -> s === defaultTrack
                    defaultTrack != null && s.source.type == StreamType.Audio -> false
                    else -> s.isDefault
                }
                if (encode == null) {
                    append("stream\t").append(s.source)
                } else {
                    append("encode\t").append(s.source).append('\t').append(encode.codec.native)
                    encode.bitrateKbps?.let { append("\tbitrate=").append(it.coerceIn(32, 1024)) }
                    encode.channels?.let { append("\tchannels=").append(it.coerceIn(1, 8)) }
                }
                isDefault?.let { append("\tdefault=").append(if (it) 1 else 0) }
                s.forced?.let { append("\tforced=").append(if (it) 1 else 0) }
                s.language?.let { append("\tlang=").append(it.clean()) }
                s.title?.let { append("\ttitle=").append(it.clean()) }
                append('\n')
            }
        }
    }

    /**
     * The plan with [tracks] added (S-1207), each right after its original (after the last audio
     * track when the plan leaves the original out), titled by [title] as the interface names it.
     */
    fun withCompatibilityTracks(tracks: List<CompatibilityTrack>, title: (CompatibilityTrack) -> String): RemuxPlan {
        if (tracks.isEmpty()) return this
        val out = streams.toMutableList()
        tracks.forEach { t ->
            val name = title(t)
            val added = PlannedStream(t.source, t.spec.codec.native, label = name, title = name, encode = t.spec)
            // after the original and the tracks already added beside it
            var at = out.indexOfLast { it.source == t.source }
            if (at < 0) at = out.indexOfLast { it.source.type == StreamType.Audio }
            if (at < 0) at = out.indexOfLast { it.source.type == StreamType.Video }
            out.add(at + 1, added)
        }
        return copy(streams = out)
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
                            s.encode?.let { e ->
                                put(
                                    "encode",
                                    JSONObject().apply {
                                        put("codec", e.codec.native)
                                        e.bitrateKbps?.let { put("bitrate_kbps", it) }
                                        e.channels?.let { put("channels", it) }
                                        put("default", e.makeDefault)
                                    },
                                )
                            }
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
                        encode = s.optJSONObject("encode")?.let { e ->
                            EncodeSpec(
                                codec = EncodeCodec.of(e.getString("codec")) ?: error("bad codec"),
                                bitrateKbps = if (e.has("bitrate_kbps")) e.getInt("bitrate_kbps") else null,
                                channels = if (e.has("channels")) e.getInt("channels") else null,
                                makeDefault = e.optBoolean("default"),
                            )
                        },
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

/** The engine's refusals (remux.h): the output is the source, already holds something, or is not a regular file. */
object RemuxRefusal {
    const val SAME_FILE = "same_file"
    const val OUTPUT_NOT_EMPTY = "output_not_empty"
    const val OUTPUT_NOT_FILE = "output_not_file"
}

/**
 * S-1207: an added track in the engine's stats: what it was made from and into, and its sanity
 * check: [seconds] against its source's [sourceSeconds] (the engine fails a run beyond half a
 * second), [startOffsetSeconds] from the source's first timestamp, the silence put into the
 * source's gaps and the packets the decoder refused.
 */
data class EncodedTrackResult(
    val sourceCodec: String,
    val channels: Int,
    val layout: String,
    val sampleRate: Int,
    val bitRate: Long,
    val framesDecoded: Long,
    val decodeErrors: Long,
    val framesDropped: Long,
    val paddedSeconds: Double,
    val sourceSeconds: Double,
    val seconds: Double,
    val startOffsetSeconds: Double,
)

/** A stream's count in the engine's stats; [encoded] for an added track. */
data class RemuxStreamResult(
    val source: StreamRef?,
    val sourceIndex: Int,
    val outputIndex: Int,
    val codec: String,
    val packets: Long,
    val bytes: Long,
    val encoded: EncodedTrackResult? = null,
)

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
    /** "same_file", "output_not_empty" or "output_not_file" when the engine refused before writing a byte; empty otherwise. */
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
                        val e = s.optJSONObject("encoded")
                        RemuxStreamResult(
                            StreamRef.parse(s.optString("ref")), s.optInt("source_index"), s.optInt("output_index"), s.optString("codec"),
                            s.optLong("packets"), s.optLong("bytes"),
                            e?.let {
                                EncodedTrackResult(
                                    sourceCodec = it.optString("source_codec"),
                                    channels = it.optInt("channels"),
                                    layout = it.optString("layout"),
                                    sampleRate = it.optInt("sample_rate"),
                                    bitRate = it.optLong("bit_rate"),
                                    framesDecoded = it.optLong("frames_decoded"),
                                    decodeErrors = it.optLong("decode_errors"),
                                    framesDropped = it.optLong("frames_dropped"),
                                    paddedSeconds = it.optDouble("padded_seconds", 0.0),
                                    sourceSeconds = it.optDouble("source_seconds", 0.0),
                                    seconds = it.optDouble("seconds", 0.0),
                                    startOffsetSeconds = it.optDouble("start_offset_seconds", 0.0),
                                )
                            },
                        )
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
