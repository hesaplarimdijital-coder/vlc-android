package app.nagmedar.ui.tvlab

import androidx.compose.runtime.Immutable
import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import app.nagmedar.ui.library.mainAudio

/** How a finding weighs: the film will not play right, it may not, or it plays as it is. */
enum class FindingLevel { Critical, Warning, Ok }

/**
 * What the check found (S-1201, S-1203). The interface words each kind with its [subject] (a
 * track or a stream as the analyser names it) and its numbers ([values]); the tests read the
 * kinds. Ordered as they show: the critical first.
 */
enum class FindingKind {
    /** The container is not one the device opens. */
    Container,

    /** The video codec is not one it decodes: the film needs re-encoding. */
    VideoCodec,

    /** Larger than the device shows: [values] the device's width and height. */
    Resolution,

    /** More frames a second than it shows: [values] its rate. */
    FrameRate,

    /** More bits a colour than it decodes. */
    BitDepth,

    /** Dolby Vision profile 7, two layers, on a device that plays only single-layer profiles. */
    DolbyVisionDualLayer,

    /** Dolby Vision profile 5 on a device without it: no base layer to fall back to, purple and green. */
    DolbyVisionProfile5,

    /** Profile 5 played by Nağmedar on a device without Dolby Vision: decoded in software, reshaped by libplacebo. */
    DolbyVisionSoftware,

    /** A profile the device does not take as Dolby Vision: its base layer shows ([values] its range). */
    DolbyVisionBaseLayer,

    /** HDR10+ on a device without it: its static HDR10 metadata is used. */
    Hdr10PlusAsHdr10,

    /** HDR10 or HLG on a device without that HDR. */
    HdrUnsupported,

    /** The track a player starts with is in a codec the device does not play: no sound. */
    DefaultAudio,

    /** Another track in a codec it does not play. */
    OtherAudio,

    /** DTS-HD on a device that plays the DTS core only. */
    DtsCore,

    /** Not one track it plays. */
    NoAudio,

    /** Picture subtitles (PGS, VobSub) the device does not draw. */
    BitmapSubtitles,

    /** Text subtitles in a format it does not read. */
    TextSubtitles,

    /** The highest five seconds pass what it takes: it stutters there. [values] the peak and the limit, kbps. */
    BitrateSustained,

    /** The highest second passes what it takes: it may stutter there. [values] the peak, the limit and the seconds over it. */
    BitratePeak,

    /** The video plays as it is: [subject] its line. */
    VideoOk,

    /** The sound plays as it is: [subject] the track a player starts with. */
    AudioOk,

    /** The bitrate stays under what it takes: [values] the peak and the limit, kbps. */
    BitrateOk,
}

@Immutable
data class Finding(
    val kind: FindingKind,
    val level: FindingLevel,
    /** The stream or track: "DTS-HD MA 5.1 · Tür", "HEVC Main 10 · 3840×2160". */
    val subject: String = "",
    val values: List<Long> = emptyList(),
)

/** The check of one film against one profile. */
@Immutable
data class CheckReport(val profile: DeviceProfile, val findings: List<Finding>) {
    val critical: Int get() = findings.count { it.level == FindingLevel.Critical }
    val warnings: Int get() = findings.count { it.level == FindingLevel.Warning }

    /** Whether the video can be kept as it is (a remux) rather than encoded again. */
    val videoKept: Boolean
        get() = findings.none { it.kind in setOf(FindingKind.VideoCodec, FindingKind.Resolution, FindingKind.BitDepth) }
}

/**
 * The rules of S-1203: [tech] (the probe's, S-1201) and [bitrate] (the analysis, S-1202, when it
 * is done) against [profile]. Every stream gets a finding: what keeps the film from playing
 * right, what may, and what plays as it is. [describe] names the streams as the interface does.
 */
class ProfileCheck(private val describe: StreamNames) {

    /** How the interface names a film's streams (the library's labels, in its language). */
    interface StreamNames {
        fun video(video: VideoTech): String

        fun audio(track: AudioTech): String

        fun subtitles(tracks: List<SubtitleTech>): String
    }

    fun check(tech: MediaTech, profile: DeviceProfile, bitrate: BitrateAnalysis?): CheckReport {
        val findings = mutableListOf<Finding>()
        if (tech.container.isNotEmpty() && !profile.allowsContainer(tech.container)) {
            findings += Finding(FindingKind.Container, FindingLevel.Critical, tech.container)
        }
        tech.video?.let { findings += video(it, profile) }
        findings += audio(tech, profile)
        findings += subtitles(tech, profile)
        bitrate?.let { findings += bitrate(it, profile) }
        return CheckReport(profile, findings.sortedBy { it.level.ordinal })
    }

    private fun video(video: VideoTech, profile: DeviceProfile): List<Finding> {
        val found = mutableListOf<Finding>()
        val line = describe.video(video)
        if (!profile.allowsVideo(video.codec)) found += Finding(FindingKind.VideoCodec, FindingLevel.Critical, line)
        // turned films (portrait phone videos) fit when their sides do, either way
        val long = maxOf(video.width, video.height)
        val short = minOf(video.width, video.height)
        if (long > maxOf(profile.maxWidth, profile.maxHeight) || short > minOf(profile.maxWidth, profile.maxHeight)) {
            found += Finding(FindingKind.Resolution, FindingLevel.Critical, line, listOf(profile.maxWidth.toLong(), profile.maxHeight.toLong()))
        }
        if (video.fps > profile.maxFps + FPS_SLACK) {
            found += Finding(FindingKind.FrameRate, FindingLevel.Warning, line, listOf(profile.maxFps.toLong()))
        }
        if (video.bitDepth > profile.maxBitDepth) found += Finding(FindingKind.BitDepth, FindingLevel.Critical, line, listOf(profile.maxBitDepth.toLong()))
        // the stream decodes as it is: said even when its dynamic range brings a finding of its own
        if (found.isEmpty()) found += Finding(FindingKind.VideoOk, FindingLevel.Ok, line)
        found += range(video, profile)
        return found
    }

    private fun range(video: VideoTech, profile: DeviceProfile): List<Finding> {
        val hdr = profile.hdr
        return when (video.dynamicRange) {
            DynamicRange.DolbyVision -> {
                val dvShown = HdrFormat.DolbyVision in hdr && video.dvProfile in profile.dvProfiles
                when {
                    dvShown -> emptyList()
                    video.dvProfile == 7 && HdrFormat.DolbyVision in hdr ->
                        listOf(Finding(FindingKind.DolbyVisionDualLayer, FindingLevel.Critical, "Dolby Vision P7" + video.dvEnhancement.let { if (it.isEmpty()) "" else " $it" }))
                    video.dvProfile == 5 && profile.nagmedar -> listOf(Finding(FindingKind.DolbyVisionSoftware, FindingLevel.Warning, "Dolby Vision P5"))
                    video.dvProfile == 5 -> listOf(Finding(FindingKind.DolbyVisionProfile5, FindingLevel.Critical, "Dolby Vision P5"))
                    else -> {
                        // the base layer shows: HDR10, HLG or SDR, as the device takes it
                        val base = video.baseLayer
                        val baseFormat = when (base) {
                            DynamicRange.HDR10, DynamicRange.HDR10Plus -> HdrFormat.HDR10
                            DynamicRange.HLG -> HdrFormat.HLG
                            else -> null
                        }
                        val level = if (baseFormat == null || baseFormat in hdr) FindingLevel.Warning else FindingLevel.Critical
                        listOf(Finding(FindingKind.DolbyVisionBaseLayer, level, "Dolby Vision P${video.dvProfile}", listOf(base.ordinal.toLong())))
                    }
                }
            }
            DynamicRange.HDR10Plus -> when {
                HdrFormat.HDR10Plus in hdr -> emptyList()
                HdrFormat.HDR10 in hdr -> listOf(Finding(FindingKind.Hdr10PlusAsHdr10, FindingLevel.Warning, "HDR10+"))
                else -> listOf(Finding(FindingKind.HdrUnsupported, FindingLevel.Critical, "HDR10+"))
            }
            DynamicRange.HDR10 -> if (HdrFormat.HDR10 in hdr) emptyList() else listOf(Finding(FindingKind.HdrUnsupported, FindingLevel.Critical, "HDR10"))
            DynamicRange.HLG -> if (HdrFormat.HLG in hdr) emptyList() else listOf(Finding(FindingKind.HdrUnsupported, FindingLevel.Warning, "HLG"))
            DynamicRange.SDR -> emptyList()
        }
    }

    private fun audio(tech: MediaTech, profile: DeviceProfile): List<Finding> {
        if (tech.audio.isEmpty()) return emptyList()
        val found = mutableListOf<Finding>()
        val main = tech.mainAudio()
        val playable = tech.audio.filter { profile.allowsAudio(audioKey(it)) || (audioKey(it) == "dtshd" && profile.allowsAudio("dts")) }
        if (playable.isEmpty()) {
            return listOf(Finding(FindingKind.NoAudio, FindingLevel.Critical, describe.audio(main ?: tech.audio.first())))
        }
        tech.audio.forEach { track ->
            val key = audioKey(track)
            val name = describe.audio(track)
            when {
                profile.allowsAudio(key) -> Unit
                key == "dtshd" && profile.allowsAudio("dts") -> found += Finding(FindingKind.DtsCore, FindingLevel.Warning, name)
                track == main -> found += Finding(FindingKind.DefaultAudio, FindingLevel.Critical, name)
                else -> found += Finding(FindingKind.OtherAudio, FindingLevel.Warning, name)
            }
        }
        if (found.isEmpty() && main != null) found += Finding(FindingKind.AudioOk, FindingLevel.Ok, describe.audio(main))
        return found
    }

    private fun subtitles(tech: MediaTech, profile: DeviceProfile): List<Finding> {
        val unsupported = tech.subtitles.filter { !profile.allowsSubtitle(subtitleKey(it)) }
        if (unsupported.isEmpty()) return emptyList()
        val bitmap = unsupported.filter { subtitleKey(it) in BITMAP }
        val text = unsupported.filter { subtitleKey(it) !in BITMAP }
        return listOfNotNull(
            bitmap.takeIf { it.isNotEmpty() }?.let { Finding(FindingKind.BitmapSubtitles, FindingLevel.Warning, describe.subtitles(it)) },
            text.takeIf { it.isNotEmpty() }?.let { Finding(FindingKind.TextSubtitles, FindingLevel.Warning, describe.subtitles(it)) },
        )
    }

    private fun bitrate(analysis: BitrateAnalysis, profile: DeviceProfile): List<Finding> {
        val limit = profile.limitKbps
        if (limit <= 0 || analysis.seconds == 0) return emptyList()
        val peak = analysis.peakSecondKbps.toLong()
        val sustained = analysis.peakFiveSecondsKbps.toLong()
        return listOf(
            when {
                sustained > limit -> Finding(FindingKind.BitrateSustained, FindingLevel.Critical, values = listOf(sustained, limit.toLong(), analysis.secondsOver(limit).toLong()))
                peak > limit -> Finding(FindingKind.BitratePeak, FindingLevel.Warning, values = listOf(peak, limit.toLong(), analysis.secondsOver(limit).toLong()))
                else -> Finding(FindingKind.BitrateOk, FindingLevel.Ok, values = listOf(peak, limit.toLong()))
            },
        )
    }

    private companion object {
        /** 23.976 is under 24, 59.94 under 60: a hair over the limit is the same rate. */
        const val FPS_SLACK = 0.05

        val BITMAP = setOf("pgs", "vobsub", "dvb")
    }
}
