package app.nagmedar.ui.tvlab

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.BitrateAnalysis
import app.nagmedar.lab.DiskSpeed
import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import app.nagmedar.ui.R
import app.nagmedar.ui.library.audioLine
import app.nagmedar.ui.library.bitrateLabel
import app.nagmedar.ui.library.frameRateLabel
import app.nagmedar.ui.library.subtitleLine
import app.nagmedar.ui.library.videoLine
import java.text.NumberFormat
import java.util.Locale

/** What the TV Lab screen shows (S-1201 … S-1204): the controller's state, or a sample's. */
@Immutable
data class TvLabView(
    /** Whether this build has the native tools. */
    val available: Boolean,
    val file: TvLabFile?,
    val tech: MediaTech?,
    val probe: Step,
    val bitrate: BitrateAnalysis?,
    val bitrateStep: Step,
    val drive: DriveInfo?,
    val disk: DiskSpeed?,
    val diskStep: Step,
    val elapsedMs: Long?,
    val profiles: List<DeviceProfile>,
    val profile: DeviceProfile,
) {
    /** Whether a step runs: the file is being read or measured. */
    val running: Boolean get() = probe is Step.Running || bitrateStep is Step.Running || diskStep is Step.Running

    companion object {
        /** Nothing opened yet, [profile] the target. */
        fun empty(profile: DeviceProfile, profiles: List<DeviceProfile> = listOf(profile), available: Boolean = true) = TvLabView(
            available = available,
            file = null,
            tech = null,
            probe = Step.Idle,
            bitrate = null,
            bitrateStep = Step.Idle,
            drive = null,
            disk = null,
            diskStep = Step.Idle,
            elapsedMs = null,
            profiles = profiles,
            profile = profile,
        )
    }
}

/** The controller's state as the screen shows it; read in composition, it recomposes on every change. */
fun TvLabController.view(): TvLabView =
    TvLabView(available, file, tech, probe, bitrate, bitrateStep, drive, disk, diskStep, elapsedMs, profileList, profile)

/** What the chart is saved as. */
enum class ChartFormat(val mime: String, val extension: String) {
    Png("image/png", "png"),
    Csv("text/csv", "csv"),
}

/** What the screen asks for. */
interface TvLabActions {
    fun chooseFile()

    fun chooseProfile(id: String)

    fun editProfile()

    fun newProfile()

    fun importProfile()

    fun measureDiskAgain()

    fun analyseBitrate()

    fun stop()

    fun showReport()

    fun exportChart(format: ChartFormat)
}

/** A screen that asks for nothing: previews and screenshots. */
object NoTvLabActions : TvLabActions {
    override fun chooseFile() = Unit

    override fun chooseProfile(id: String) = Unit

    override fun editProfile() = Unit

    override fun newProfile() = Unit

    override fun importProfile() = Unit

    override fun measureDiskAgain() = Unit

    override fun analyseBitrate() = Unit

    override fun stop() = Unit

    override fun showReport() = Unit

    override fun exportChart(format: ChartFormat) = Unit
}

/** A finding in words: what, why, and what to do about it. */
@Immutable
data class FindingText(val title: String, val why: String, val fix: String)

/** The streams as the interface names them, in [locale]: the check's [ProfileCheck.StreamNames]. */
class LocalizedStreamNames(private val res: Resources, private val locale: Locale) : ProfileCheck.StreamNames {
    override fun video(video: VideoTech): String =
        listOfNotNull(videoLine(video), video.fps.takeIf { it > 0 }?.let { frameRateLabel(it, locale) }).joinToString(" · ")

    override fun audio(track: AudioTech): String = audioLine(track, locale, res.getString(R.string.nag_tech_commentary))

    override fun subtitles(tracks: List<SubtitleTech>): String = subtitleLine(tracks, locale)
}

/** The check of [view]'s film, or null before its streams are known. */
fun checkOf(view: TvLabView, names: ProfileCheck.StreamNames): CheckReport? =
    view.tech?.let { ProfileCheck(names).check(it, view.profile, view.bitrate?.takeIf { analysis -> analysis.complete }) }

/** A bitrate in kbps as the interface writes it: "118 Mbps". */
fun kbpsLabel(kbps: Long, locale: Locale): String = bitrateLabel(kbps * 1000, locale)

/** "HDR10", "HLG", "SDR": a dynamic range in a sentence. */
fun rangeName(range: DynamicRange): String = when (range) {
    DynamicRange.HDR10Plus -> HdrFormat.HDR10Plus.label
    DynamicRange.DolbyVision -> HdrFormat.DolbyVision.label
    else -> range.name
}

/** "%42" in Turkish, "42%" in English. */
fun percentLabel(fraction: Float, locale: Locale): String = NumberFormat.getPercentInstance(locale).format(fraction.coerceIn(0f, 1f).toDouble())

/** A rate in MB/s: "412 MB/s", "28,5 MB/s". */
fun megabytesLabel(res: Resources, megabytesPerSecond: Double, locale: Locale): String {
    val number = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = if (megabytesPerSecond >= 100) 0 else 1
        minimumFractionDigits = 0
    }.format(megabytesPerSecond)
    return res.getString(R.string.nag_tvlab_mb_per_s, number)
}

/** "42 sn", "3 dk 12 sn". */
fun elapsedLabel(res: Resources, millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0).toInt()
    return if (seconds < 60) {
        res.getString(R.string.nag_duration_seconds, seconds)
    } else {
        res.getString(R.string.nag_tvlab_minutes_seconds, seconds / 60, seconds % 60)
    }
}

/** A second of the film as a clock: "0:42", "1:12:33". */
fun clockLabel(second: Int): String {
    val hours = second / 3600
    val minutes = second % 3600 / 60
    val seconds = second % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds) else "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/** How the device gets the file, in words. */
fun sourceLabel(res: Resources, source: SourceLink): String = res.getString(
    when (source) {
        SourceLink.Usb2 -> R.string.nag_tvlab_source_usb2
        SourceLink.Usb3 -> R.string.nag_tvlab_source_usb3
        SourceLink.Network -> R.string.nag_tvlab_source_network
        SourceLink.Local -> R.string.nag_tvlab_source_local
    },
)

/** "LG OLED B4 · webOS 24 · USB". */
fun DeviceProfile.label(): String = listOf(name, detail).filter { it.isNotBlank() }.joinToString(" · ")

/** What a level is called. */
fun levelLabel(res: Resources, level: FindingLevel): String = res.getString(
    when (level) {
        FindingLevel.Critical -> R.string.nag_tvlab_level_critical
        FindingLevel.Warning -> R.string.nag_tvlab_level_warning
        FindingLevel.Ok -> R.string.nag_tvlab_level_ok
    },
)

/**
 * A finding in words (S-1203): its title (the stream it is about), why it matters on [profile]'s
 * device, and what makes the film play.
 */
fun findingText(res: Resources, finding: Finding, profile: DeviceProfile, locale: Locale): FindingText {
    val device = profile.name
    val subject = finding.subject
    val values = finding.values
    fun s(id: Int, vararg args: Any): String = res.getString(id, *args)
    val noChange = s(R.string.nag_tvlab_f_no_change)
    return when (finding.kind) {
        FindingKind.Container -> FindingText(
            s(R.string.nag_tvlab_f_container_title, subject),
            s(R.string.nag_tvlab_f_container_why, device),
            s(R.string.nag_tvlab_f_container_fix),
        )
        FindingKind.VideoCodec -> FindingText(subject, s(R.string.nag_tvlab_f_codec_why, device), s(R.string.nag_tvlab_f_codec_fix))
        FindingKind.Resolution -> FindingText(
            subject,
            s(R.string.nag_tvlab_f_at_most, device, "${values.getOrElse(0) { 0L }}×${values.getOrElse(1) { 0L }}"),
            s(R.string.nag_tvlab_f_resolution_fix),
        )
        FindingKind.FrameRate -> FindingText(
            subject,
            s(R.string.nag_tvlab_f_at_most, device, frameRateLabel(values.getOrElse(0) { 0L }.toDouble(), locale)),
            s(R.string.nag_tvlab_f_fps_fix),
        )
        FindingKind.BitDepth -> {
            val depth = values.getOrElse(0) { 8L }.toInt()
            FindingText(subject, s(R.string.nag_tvlab_f_depth_why, device, depth), s(R.string.nag_tvlab_f_depth_fix, depth))
        }
        FindingKind.DolbyVisionDualLayer -> FindingText(
            s(R.string.nag_tvlab_f_dv7_title, subject),
            s(R.string.nag_tvlab_f_dv7_why, device),
            s(R.string.nag_tvlab_f_dv7_fix),
        )
        FindingKind.DolbyVisionProfile5 -> FindingText(subject, s(R.string.nag_tvlab_f_dv5_why, device), s(R.string.nag_tvlab_f_dv5_fix))
        FindingKind.DolbyVisionSoftware -> FindingText(subject, s(R.string.nag_tvlab_f_dv5_soft_why), s(R.string.nag_tvlab_f_dv5_soft_fix))
        FindingKind.DolbyVisionBaseLayer -> {
            val base = rangeName(DynamicRange.entries.getOrElse(values.getOrElse(0) { 0L }.toInt()) { DynamicRange.SDR })
            FindingText(
                subject,
                s(R.string.nag_tvlab_f_dv_base_why, device, base),
                if (finding.level == FindingLevel.Critical) s(R.string.nag_tvlab_f_dv_base_bad, base) else s(R.string.nag_tvlab_f_dv_base_fix, base),
            )
        }
        FindingKind.Hdr10PlusAsHdr10 -> FindingText(subject, s(R.string.nag_tvlab_f_hdr10plus_why, device), s(R.string.nag_tvlab_f_hdr10plus_fix))
        FindingKind.HdrUnsupported -> FindingText(
            subject,
            s(R.string.nag_tvlab_f_hdr_why, device, subject),
            if (finding.level == FindingLevel.Critical) s(R.string.nag_tvlab_f_hdr_fix) else s(R.string.nag_tvlab_f_hlg_fix),
        )
        FindingKind.DefaultAudio -> FindingText(
            s(R.string.nag_tvlab_f_default_audio_title, subject),
            s(R.string.nag_tvlab_f_default_audio_why, device),
            s(R.string.nag_tvlab_f_default_audio_fix),
        )
        FindingKind.OtherAudio -> FindingText(subject, s(R.string.nag_tvlab_f_other_audio_why, device), s(R.string.nag_tvlab_f_other_audio_fix))
        FindingKind.DtsCore -> FindingText(subject, s(R.string.nag_tvlab_f_dts_core_why, device), s(R.string.nag_tvlab_f_dts_core_fix))
        FindingKind.NoAudio -> FindingText(
            s(R.string.nag_tvlab_f_no_audio_title),
            s(R.string.nag_tvlab_f_no_audio_why, device, subject),
            s(R.string.nag_tvlab_f_no_audio_fix),
        )
        FindingKind.BitmapSubtitles -> FindingText(subject, s(R.string.nag_tvlab_f_bitmap_subs_why, device), s(R.string.nag_tvlab_f_bitmap_subs_fix))
        FindingKind.TextSubtitles -> FindingText(subject, s(R.string.nag_tvlab_f_text_subs_why, device), s(R.string.nag_tvlab_f_text_subs_fix))
        FindingKind.BitrateSustained -> FindingText(
            s(R.string.nag_tvlab_f_sustained_title, kbpsLabel(values.getOrElse(0) { 0L }, locale)),
            s(R.string.nag_tvlab_f_sustained_why, kbpsLabel(values.getOrElse(1) { 0L }, locale), device),
            s(R.string.nag_tvlab_f_sustained_fix),
        )
        FindingKind.BitratePeak -> {
            val over = values.getOrElse(2) { 0L }.toInt()
            FindingText(
                s(R.string.nag_tvlab_f_peak_title, kbpsLabel(values.getOrElse(0) { 0L }, locale)),
                s(R.string.nag_tvlab_f_peak_why, kbpsLabel(values.getOrElse(1) { 0L }, locale), res.getQuantityString(R.plurals.nag_tvlab_seconds, over, over)),
                s(R.string.nag_tvlab_f_peak_fix),
            )
        }
        FindingKind.VideoOk -> FindingText(subject, s(R.string.nag_tvlab_f_video_ok_why, device), noChange)
        FindingKind.AudioOk -> FindingText(subject, s(R.string.nag_tvlab_f_audio_ok_why, device), noChange)
        FindingKind.BitrateOk -> FindingText(
            s(R.string.nag_tvlab_f_peak_title, kbpsLabel(values.getOrElse(0) { 0L }, locale)),
            s(R.string.nag_tvlab_f_bitrate_ok_why, kbpsLabel(values.getOrElse(1) { 0L }, locale)),
            noChange,
        )
    }
}

/** The result in a few words: "2 kritik · 2 uyarı", "Sorun yok". */
fun resultCounts(res: Resources, report: CheckReport): String = when {
    report.critical == 0 && report.warnings == 0 -> res.getString(R.string.nag_tvlab_no_problems)
    else -> listOfNotNull(
        report.critical.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_tvlab_critical, it, it) },
        report.warnings.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.nag_tvlab_warnings, it, it) },
    ).joinToString(" · ")
}

/** What the result means for playing the film. */
fun resultVerdict(res: Resources, report: CheckReport): String = res.getString(
    when {
        report.findings.any { it.kind == FindingKind.BitrateSustained } -> R.string.nag_tvlab_result_stutter
        report.critical > 0 -> R.string.nag_tvlab_result_wont_play
        report.warnings > 0 -> R.string.nag_tvlab_result_warnings
        else -> R.string.nag_tvlab_result_fine
    },
)
