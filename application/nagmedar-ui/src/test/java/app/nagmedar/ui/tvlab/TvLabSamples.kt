package app.nagmedar.ui.tvlab

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.DiskSpeed
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.StreamBitrate
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The TV Lab's sample film, as the mockup has it: a UHD Blu-ray remux with Dolby Vision profile 7
 * (FEL), TrueHD Atmos, DTS-HD MA and a commentary, PGS and SRT subtitles, on a SAMSUNG T7.
 */

fun sampleVideo(
    dvProfile: Int = 7,
    compatibility: Int = 6,
    enhancement: String = "FEL",
    codec: String = "hevc",
    width: Int = 3840,
    height: Int = 2160,
    fps: Double = 23.976,
    bitDepth: Int = 10,
    hdr10Plus: Boolean = false,
    transfer: String = "smpte2084",
) = VideoTech(
    codec = codec,
    profile = "Main 10",
    width = width,
    height = height,
    fps = fps,
    bitDepth = bitDepth,
    transfer = transfer,
    primaries = "bt2020",
    matrix = "bt2020nc",
    maxLuminance = 1000.0,
    minLuminance = 0.0001,
    maxCll = 1000,
    maxFall = 400,
    hdr10Plus = hdr10Plus,
    dvProfile = dvProfile,
    dvLevel = if (dvProfile > 0) 6 else 0,
    dvCompatibility = compatibility,
    dvEnhancement = enhancement,
    dvEnhancementLayer = enhancement.isNotEmpty(),
    dvRpu = dvProfile > 0,
)

fun sampleAudio(codec: String, profile: String, channels: Int, language: String, default: Boolean = false, commentary: Boolean = false) = AudioTech(
    codec = codec,
    profile = profile,
    channels = channels,
    layout = when (channels) {
        8 -> "7.1"
        6 -> "5.1(side)"
        else -> "stereo"
    },
    sampleRate = 48_000,
    bits = if (codec == "ac3") 0 else 24,
    bitrate = 0,
    language = language,
    title = "",
    isDefault = default,
    forced = false,
    commentary = commentary,
)

fun sampleSubtitle(codec: String, language: String) = SubtitleTech(codec, language, "", isDefault = false, forced = false, hearingImpaired = false)

fun sampleTech(video: VideoTech? = sampleVideo(), audio: List<AudioTech>? = null, subtitles: List<SubtitleTech>? = null, format: String = "matroska,webm") = MediaTech(
    format = format,
    brand = "",
    durationMs = 7_905_000,
    sizeBytes = 58_400_000_000,
    bitrate = 59_100_000,
    chapters = 32,
    video = video,
    audio = audio ?: listOf(
        sampleAudio("truehd", "Dolby TrueHD + Dolby Atmos", 8, "eng"),
        sampleAudio("dts", "DTS-HD MA", 6, "tur", default = true),
        sampleAudio("ac3", "", 2, "tur", commentary = true),
    ),
    subtitles = subtitles ?: listOf(sampleSubtitle("hdmv_pgs_subtitle", "tur"), sampleSubtitle("hdmv_pgs_subtitle", "eng"), sampleSubtitle("subrip", "tur")),
)

/**
 * A bitrate like the mockup's: about 62 Mbps, waving, with three seconds over 100 (118, 109 and
 * 104 Mbps) along [seconds].
 */
fun sampleBitrate(seconds: Int = 7_905, complete: Boolean = true): BitrateAnalysis {
    val spikes = mapOf(seconds * 47 / 139 to 118_000, seconds * 88 / 139 to 109_000, seconds * 121 / 139 to 104_000)
    val total = IntArray(seconds) { i ->
        val t = i * 139.0 / seconds
        val wave = 62 + 11 * sin(t / 9) + 6 * sin(t / 3.1) + 4 * cos(t / 1.7)
        spikes[i] ?: ((wave + 6 + 8 * abs(sin(i * 1.31))) * 1000).toInt()
    }
    val video = IntArray(seconds) { total[it] - 4_800 }
    val average = total.map { it.toLong() }.sum() / seconds
    val analysis = BitrateAnalysis(
        complete = complete,
        durationMs = seconds * 1000L,
        sizeBytes = 58_400_000_000,
        total = total,
        video = video,
        averageKbps = average.toInt(),
        peakSecondKbps = total.max(),
        peakFiveSecondsKbps = 0,
        videoAverageKbps = (average - 4_800).toInt(),
        videoPeakSecondKbps = video.max(),
        videoPeakFiveSecondsKbps = 0,
        streams = listOf(StreamBitrate(0, "video", "hevc", 55_000_000_000, (average - 4_800).toInt(), video.max())),
    )
    return analysis.copy(peakFiveSecondsKbps = analysis.peakOf(total, 5), videoPeakFiveSecondsKbps = analysis.peakOf(video, 5))
}

val sampleDrive = DriveInfo(
    name = "SAMSUNG T7",
    filesystem = "exFAT",
    totalBytes = 2_000_398_934_016,
    freeBytes = 1_231_000_000_000,
    removable = true,
    usbVersion = "3.20",
)

val sampleDisk = DiskSpeed(bytes = 1_073_741_824, ms = 2_604, megabytesPerSecond = 412.3, firstMs = 11, complete = true)

val sampleFile = TvLabFile("content://com.android.externalstorage.documents/document/1234-ABCD%3AFilmler%2FKuzey_Isigi_2160p_UHD_Remux.mkv", "Kuzey_Isigi_2160p_UHD_Remux.mkv", 58_400_000_000)

/** The finished analysis of the sample film against [profile]. */
fun sampleTvLabView(profile: DeviceProfile = BuiltInProfiles.lgB4Usb) = TvLabView(
    available = true,
    file = sampleFile,
    tech = sampleTech(),
    probe = Step.Done,
    bitrate = sampleBitrate(),
    bitrateStep = Step.Done,
    drive = sampleDrive,
    disk = sampleDisk,
    diskStep = Step.Done,
    elapsedMs = 42_000,
    profiles = BuiltInProfiles.all,
    profile = profile,
)

/** The sample film read, its drive measured, its packets being counted. */
fun sampleRunningView() = sampleTvLabView().copy(bitrate = null, bitrateStep = Step.Running(0.42f), elapsedMs = null)
