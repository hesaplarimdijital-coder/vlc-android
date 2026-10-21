package app.nagmedar.ui.transcode

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.R
import org.json.JSONObject

/**
 * S-1303: what a conversion writes into, as libVLC's stream output muxes it: MP4 and MPEG-TS by
 * VLC's own muxers, Matroska and FLAC through its avformat muxer, MP3 as the raw stream, Opus
 * and Vorbis in Ogg, PCM in WAV. No WebM: the published libVLC's FFmpeg has Matroska's muxer
 * but not WebM's, and VLC has no muxer of its own for either; VP8 goes into MKV.
 */
enum class TranscodeContainer(
    val extension: String,
    val mime: String,
    /** The muxer in the chain: "mp4", "avformat{mux=matroska}". */
    val mux: String,
    val video: Set<VideoCodec>,
    val audio: Set<AudioCodec>,
    /** Subtitles can be kept as tracks. */
    val keepsSubtitles: Boolean = false,
    val label: String,
) {
    Mp4("mp4", "video/mp4", "mp4", setOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.Mpeg4, VideoCodec.Copy), setOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Ac3, AudioCodec.Copy), label = "MP4"),
    Mkv("mkv", "video/x-matroska", "avformat{mux=matroska}", VideoCodec.entries.toSet(), AudioCodec.entries.toSet(), keepsSubtitles = true, label = "MKV"),
    Ts("ts", "video/mp2t", "ts", setOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.Mpeg4, VideoCodec.Copy), setOf(AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Ac3, AudioCodec.Copy), label = "MPEG-TS"),
    M4a("m4a", "audio/mp4", "mp4", emptySet(), setOf(AudioCodec.Aac), label = "M4A"),
    Mp3("mp3", "audio/mpeg", "raw", emptySet(), setOf(AudioCodec.Mp3), label = "MP3"),
    Opus("opus", "audio/ogg", "ogg", emptySet(), setOf(AudioCodec.Opus), label = "Opus"),
    Ogg("ogg", "audio/ogg", "ogg", emptySet(), setOf(AudioCodec.Vorbis, AudioCodec.Flac), label = "Ogg"),
    Flac("flac", "audio/flac", "avformat{mux=flac}", emptySet(), setOf(AudioCodec.Flac), label = "FLAC"),
    Wav("wav", "audio/wav", "wav", emptySet(), setOf(AudioCodec.Pcm), label = "WAV");

    val holdsVideo: Boolean get() = video.isNotEmpty()
}

/**
 * S-1303: a video encoder by its fourcc in the chain; [Copy] passes the stream as it is. Whether
 * this build of libVLC carries it is [EncoderSupport]'s to say.
 */
enum class VideoCodec(val fourcc: String?, val label: String) {
    H264("h264", "H.264"),
    H265("hevc", "H.265"),
    Mpeg4("mp4v", "MPEG-4"),
    Vp8("VP80", "VP8"),
    Copy(null, "");
}

/** S-1303: an audio encoder by its fourcc; [Copy] passes the stream as it is. */
enum class AudioCodec(val fourcc: String?, val label: String, val lossless: Boolean = false, val maxChannels: Int = 8) {
    Aac("mp4a", "AAC", maxChannels = 6),
    Mp3("mp3", "MP3", maxChannels = 2),
    Opus("opus", "Opus", maxChannels = 2),
    Vorbis("vorb", "Vorbis"),
    Flac("flac", "FLAC", lossless = true),
    Pcm("s16l", "PCM", lossless = true),
    Ac3("a52", "AC-3", maxChannels = 6),
    Copy(null, "");
}

/** S-1303: what happens to a film's subtitles: left out, kept as tracks (MKV), or burnt into the picture. */
enum class SubtitleMode(@StringRes val label: Int) {
    None(R.string.nag_conv_subs_none),
    Keep(R.string.nag_conv_subs_keep),
    Burn(R.string.nag_conv_subs_burn),
}

/** S-1303: the video of a profile: the encoder, its bit rate, the largest height (none: the source's), a frame rate (none: the source's). */
@Immutable
data class VideoSettings(
    val codec: VideoCodec,
    val bitrateKbps: Int = 4000,
    val maxHeight: Int? = null,
    val fps: Int? = null,
)

/** S-1303: the audio of a profile: the encoder, its bit rate (not for lossless ones), channels, a sample rate (none: the source's). */
@Immutable
data class AudioSettings(
    val codec: AudioCodec,
    val bitrateKbps: Int = 192,
    val channels: Int = 2,
    val sampleRate: Int? = null,
)

/**
 * S-1303: a conversion profile: the container, the video (null: none, an audio file) and the audio
 * (null: none), what happens to the subtitles. The presets have negative ids and a translated
 * name ([nameRes]); the user's own are kept in Nağmedar's database.
 */
@Immutable
data class TranscodeProfile(
    val id: Long = 0,
    val name: String,
    val container: TranscodeContainer,
    val video: VideoSettings?,
    val audio: AudioSettings?,
    val subtitles: SubtitleMode = SubtitleMode.None,
    @StringRes val nameRes: Int? = null,
) {
    val builtin: Boolean get() = id < 0

    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("container", container.name)
        video?.let { v ->
            put("video", JSONObject().put("codec", v.codec.name).put("kbps", v.bitrateKbps).put("max_height", v.maxHeight ?: 0).put("fps", v.fps ?: 0))
        }
        audio?.let { a ->
            put("audio", JSONObject().put("codec", a.codec.name).put("kbps", a.bitrateKbps).put("channels", a.channels).put("rate", a.sampleRate ?: 0))
        }
        put("subtitles", subtitles.name)
    }.toString()

    companion object {
        /** A profile from [toJson]; null when the text is not one. */
        fun parse(json: String, id: Long = 0): TranscodeProfile? = runCatching {
            val o = JSONObject(json)
            val video = o.optJSONObject("video")?.let { v ->
                VideoSettings(
                    VideoCodec.valueOf(v.getString("codec")),
                    v.optInt("kbps", 4000),
                    v.optInt("max_height", 0).takeIf { it > 0 },
                    v.optInt("fps", 0).takeIf { it > 0 },
                )
            }
            val audio = o.optJSONObject("audio")?.let { a ->
                AudioSettings(AudioCodec.valueOf(a.getString("codec")), a.optInt("kbps", 192), a.optInt("channels", 2), a.optInt("rate", 0).takeIf { it > 0 })
            }
            TranscodeProfile(
                id = id,
                name = o.getString("name"),
                container = TranscodeContainer.valueOf(o.getString("container")),
                video = video,
                audio = audio,
                subtitles = SubtitleMode.valueOf(o.optString("subtitles", SubtitleMode.None.name)),
            )
        }.getOrNull()
    }
}

/**
 * S-1303: the encoders the published libVLC (4.0.0-eap29, libvlc-all) carries, as a look into
 * its libvlc.so found them: x264 for H.264, VLC's libvpx encoder for VP8, FFmpeg's MPEG-4
 * Part 2, AAC and AC-3 encoders, LAME (through FFmpeg) for MP3, VLC's FLAC, Vorbis and Opus
 * encoders. There is no H.265 encoder: x265 is not built and libVLC has no MediaCodec encoder;
 * a profile asking for one is refused.
 */
object EncoderSupport {
    fun available(codec: VideoCodec): Boolean = codec != VideoCodec.H265

    fun available(codec: AudioCodec): Boolean = true
}

/** S-1303: why a profile cannot be saved or used. */
enum class ProfileProblem(@StringRes val message: Int) {
    NameMissing(R.string.nag_conv_problem_name),
    NameTaken(R.string.nag_conv_problem_name_taken),
    Empty(R.string.nag_conv_problem_empty),
    VideoNotInContainer(R.string.nag_conv_problem_video_container),
    AudioNotInContainer(R.string.nag_conv_problem_audio_container),
    NoEncoder(R.string.nag_conv_problem_encoder),
    VideoBitrate(R.string.nag_conv_problem_video_bitrate),
    AudioBitrate(R.string.nag_conv_problem_audio_bitrate),
    Channels(R.string.nag_conv_problem_channels),
    BurnNeedsVideo(R.string.nag_conv_problem_burn),
    KeepNeedsMkv(R.string.nag_conv_problem_keep),
}

/** S-1303: the presets, and the rules a profile keeps to. */
object TranscodeProfiles {

    val VIDEO_BITRATES = 200..80_000
    val AUDIO_BITRATES = 32..512
    val HEIGHTS = listOf(2160, 1440, 1080, 720, 576, 480, 360)
    val SAMPLE_RATES = listOf(44_100, 48_000, 96_000)

    val presets: List<TranscodeProfile> = listOf(
        TranscodeProfile(
            -1, "H.264 + AAC · MP4 · 1080p", TranscodeContainer.Mp4, VideoSettings(VideoCodec.H264, 8000, 1080), AudioSettings(AudioCodec.Aac, 192, 2),
            nameRes = R.string.nag_conv_preset_h264_1080,
        ),
        TranscodeProfile(
            -2, "H.264 + AAC · MP4 · 720p", TranscodeContainer.Mp4, VideoSettings(VideoCodec.H264, 4000, 720), AudioSettings(AudioCodec.Aac, 160, 2),
            nameRes = R.string.nag_conv_preset_h264_720,
        ),
        TranscodeProfile(
            -3, "H.265 + AAC · MKV · 1080p", TranscodeContainer.Mkv, VideoSettings(VideoCodec.H265, 5000, 1080), AudioSettings(AudioCodec.Aac, 192, 2),
            SubtitleMode.Keep, nameRes = R.string.nag_conv_preset_h265,
        ),
        TranscodeProfile(
            -4, "VP8 + Vorbis · MKV · 720p", TranscodeContainer.Mkv, VideoSettings(VideoCodec.Vp8, 3000, 720), AudioSettings(AudioCodec.Vorbis, 160, 2),
            nameRes = R.string.nag_conv_preset_vp8,
        ),
        TranscodeProfile(-5, "MKV", TranscodeContainer.Mkv, VideoSettings(VideoCodec.Copy), AudioSettings(AudioCodec.Copy), SubtitleMode.Keep, nameRes = R.string.nag_conv_preset_copy),
        TranscodeProfile(-6, "MP3 · 320 kbps", TranscodeContainer.Mp3, null, AudioSettings(AudioCodec.Mp3, 320, 2), nameRes = R.string.nag_conv_preset_mp3),
        TranscodeProfile(-7, "Opus · 160 kbps", TranscodeContainer.Opus, null, AudioSettings(AudioCodec.Opus, 160, 2, 48_000), nameRes = R.string.nag_conv_preset_opus),
        TranscodeProfile(-8, "FLAC", TranscodeContainer.Flac, null, AudioSettings(AudioCodec.Flac, 0, 2), nameRes = R.string.nag_conv_preset_flac),
        TranscodeProfile(-9, "AAC · M4A · 256 kbps", TranscodeContainer.M4a, null, AudioSettings(AudioCodec.Aac, 256, 2), nameRes = R.string.nag_conv_preset_aac),
    )

    /** What is wrong with [profile]; [taken] are the other profiles' names. Empty: it can be saved and used. */
    fun validate(profile: TranscodeProfile, taken: Collection<String> = emptyList()): List<ProfileProblem> {
        val problems = mutableListOf<ProfileProblem>()
        val name = profile.name.trim()
        if (name.isEmpty()) problems += ProfileProblem.NameMissing
        if (name.isNotEmpty() && taken.any { it.trim().equals(name, ignoreCase = true) }) problems += ProfileProblem.NameTaken
        val video = profile.video
        val audio = profile.audio
        if (video == null && audio == null) problems += ProfileProblem.Empty
        if (video != null) {
            if (video.codec !in profile.container.video) problems += ProfileProblem.VideoNotInContainer
            if (!EncoderSupport.available(video.codec)) problems += ProfileProblem.NoEncoder
            if (video.codec != VideoCodec.Copy && video.bitrateKbps !in VIDEO_BITRATES) problems += ProfileProblem.VideoBitrate
        }
        if (audio != null) {
            if (audio.codec !in profile.container.audio) problems += ProfileProblem.AudioNotInContainer
            if (!EncoderSupport.available(audio.codec)) problems += ProfileProblem.NoEncoder
            if (audio.codec != AudioCodec.Copy && !audio.codec.lossless && audio.bitrateKbps !in AUDIO_BITRATES) problems += ProfileProblem.AudioBitrate
            if (audio.codec != AudioCodec.Copy && audio.channels !in 1..audio.codec.maxChannels) problems += ProfileProblem.Channels
        }
        if (profile.subtitles == SubtitleMode.Burn && (video == null || video.codec == VideoCodec.Copy)) problems += ProfileProblem.BurnNeedsVideo
        if (profile.subtitles == SubtitleMode.Keep && !profile.container.keepsSubtitles) problems += ProfileProblem.KeepNeedsMkv
        return problems.distinct()
    }

    /** [profile] made to fit [container]: the codecs it takes, the subtitles it can hold. */
    fun fitTo(profile: TranscodeProfile, container: TranscodeContainer): TranscodeProfile {
        val video = if (!container.holdsVideo) {
            null
        } else {
            val v = profile.video ?: VideoSettings(container.video.first { it != VideoCodec.Copy })
            if (v.codec in container.video) v else v.copy(codec = container.video.first { EncoderSupport.available(it) })
        }
        val audio = profile.audio?.let { a -> if (a.codec in container.audio) a else a.copy(codec = container.audio.first()) }
            ?: AudioSettings(container.audio.first())
        val subtitles = when {
            !container.holdsVideo -> SubtitleMode.None
            profile.subtitles == SubtitleMode.Keep && !container.keepsSubtitles -> SubtitleMode.None
            else -> profile.subtitles
        }
        return profile.copy(container = container, video = video, audio = audio, subtitles = subtitles)
    }
}

/**
 * S-1303: what the HLS stream is encoded as: H.264 and AAC in MPEG-TS, which every HLS player
 * plays, at a height and bit rate a local network carries; or the sound alone.
 */
enum class HlsQuality(@StringRes val label: Int, val profile: TranscodeProfile) {
    P1080(R.string.nag_hls_quality_1080, hlsProfile(VideoSettings(VideoCodec.H264, 6000, 1080))),
    P720(R.string.nag_hls_quality_720, hlsProfile(VideoSettings(VideoCodec.H264, 3000, 720))),
    P480(R.string.nag_hls_quality_480, hlsProfile(VideoSettings(VideoCodec.H264, 1200, 480))),
    Audio(R.string.nag_hls_quality_audio, hlsProfile(null)),
}

private fun hlsProfile(video: VideoSettings?) =
    TranscodeProfile(name = "HLS", container = TranscodeContainer.Ts, video = video, audio = AudioSettings(AudioCodec.Aac, 160, 2, 48_000))
