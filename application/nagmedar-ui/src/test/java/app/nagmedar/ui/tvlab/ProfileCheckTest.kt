package app.nagmedar.ui.tvlab

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.DynamicRange
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1203: the rules of a device profile, on the sample film and its variants. The streams are
 * named by their codecs here; the interface's names are TvLabScreenTest's.
 */
class ProfileCheckTest {

    private val names = object : ProfileCheck.StreamNames {
        override fun video(video: VideoTech) = video.codec

        override fun audio(track: AudioTech) = "${track.codec} ${track.language}"

        override fun subtitles(tracks: List<SubtitleTech>) = tracks.joinToString { it.codec }
    }

    private val check = ProfileCheck(names)
    private val b4 = BuiltInProfiles.lgB4Usb
    private val tablet = BuiltInProfiles.tabS11Ultra

    private fun CheckReport.kinds() = findings.map { it.kind to it.level }

    @Test
    fun theSampleFilmOnTheB4() {
        val report = check.check(sampleTech(), b4, sampleBitrate())
        assertEquals(
            listOf(
                FindingKind.DolbyVisionDualLayer to FindingLevel.Critical,
                FindingKind.DefaultAudio to FindingLevel.Critical,
                FindingKind.OtherAudio to FindingLevel.Warning,
                FindingKind.BitmapSubtitles to FindingLevel.Warning,
                FindingKind.BitratePeak to FindingLevel.Warning,
                FindingKind.VideoOk to FindingLevel.Ok,
            ),
            report.kinds(),
        )
        assertEquals(2, report.critical)
        assertEquals(3, report.warnings)
        // the dual layer goes, the base layer is copied: no re-encoding
        assertTrue(report.videoKept)
        assertEquals("Dolby Vision P7 FEL", report.findings[0].subject)
        assertEquals("dts tur", report.findings[1].subject)
        assertEquals("truehd eng", report.findings[2].subject)
        // the PGS tracks, not the SRT one
        assertEquals("hdmv_pgs_subtitle, hdmv_pgs_subtitle", report.findings[3].subject)
        // 118 Mbps over 100, three seconds over it
        assertEquals(listOf(118_000L, 100_000L, 3L), report.findings[4].values)
    }

    @Test
    fun theSampleFilmOnTheTablet() {
        val report = check.check(sampleTech(), tablet, sampleBitrate())
        assertEquals(
            listOf(
                FindingKind.DolbyVisionBaseLayer to FindingLevel.Warning,
                FindingKind.VideoOk to FindingLevel.Ok,
                FindingKind.AudioOk to FindingLevel.Ok,
            ),
            report.kinds(),
        )
        // its HDR10 base layer shows; no limit, no bitrate finding
        assertEquals(listOf(DynamicRange.HDR10.ordinal.toLong()), report.findings[0].values)
        assertEquals("dts tur", report.findings[2].subject)
    }

    @Test
    fun dolbyVisionProfiles() {
        fun range(video: VideoTech, profile: DeviceProfile) =
            check.check(sampleTech(video = video), profile, null).findings.filter { it.kind != FindingKind.VideoOk && it.kind.name.startsWith("DolbyVision") }
                .map { it.kind to it.level }
        // profile 5 and 8.1 are the B4's own
        assertEquals(emptyList<Any>(), range(sampleVideo(dvProfile = 5, compatibility = 0, enhancement = ""), b4))
        assertEquals(emptyList<Any>(), range(sampleVideo(dvProfile = 8, compatibility = 1, enhancement = ""), b4))
        // profile 5 without Dolby Vision: purple and green, unless Nağmedar reshapes it
        val noDv = b4.copy(hdr = setOf(HdrFormat.HDR10, HdrFormat.HLG), dvProfiles = emptySet())
        assertEquals(listOf(FindingKind.DolbyVisionProfile5 to FindingLevel.Critical), range(sampleVideo(dvProfile = 5, compatibility = 0, enhancement = ""), noDv))
        assertEquals(listOf(FindingKind.DolbyVisionSoftware to FindingLevel.Warning), range(sampleVideo(dvProfile = 5, compatibility = 0, enhancement = ""), tablet))
        // 8.1 shows its HDR10 base layer; on a screen without HDR that is wrong too
        assertEquals(listOf(FindingKind.DolbyVisionBaseLayer to FindingLevel.Warning), range(sampleVideo(dvProfile = 8, compatibility = 1, enhancement = ""), noDv))
        val sdrOnly = noDv.copy(hdr = emptySet())
        assertEquals(listOf(FindingKind.DolbyVisionBaseLayer to FindingLevel.Critical), range(sampleVideo(dvProfile = 8, compatibility = 1, enhancement = ""), sdrOnly))
        // 8.4's HLG base layer on a screen with HLG
        assertEquals(listOf(FindingKind.DolbyVisionBaseLayer to FindingLevel.Warning), range(sampleVideo(dvProfile = 8, compatibility = 4, enhancement = ""), noDv))
        // profile 7 on a device without Dolby Vision is its base layer, not a dual layer to fail on
        assertEquals(listOf(FindingKind.DolbyVisionBaseLayer to FindingLevel.Warning), range(sampleVideo(), noDv))
    }

    @Test
    fun hdrFormats() {
        fun hdr(video: VideoTech, profile: DeviceProfile) =
            check.check(sampleTech(video = video), profile, null).findings.filter { it.kind == FindingKind.Hdr10PlusAsHdr10 || it.kind == FindingKind.HdrUnsupported }
                .map { Triple(it.kind, it.level, it.subject) }
        val hdr10Plus = sampleVideo(dvProfile = 0, compatibility = 0, enhancement = "", hdr10Plus = true)
        val hdr10 = sampleVideo(dvProfile = 0, compatibility = 0, enhancement = "")
        val hlg = sampleVideo(dvProfile = 0, compatibility = 0, enhancement = "", transfer = "arib-std-b67").copy(maxLuminance = 0.0)
        assertEquals(listOf(Triple(FindingKind.Hdr10PlusAsHdr10, FindingLevel.Warning, "HDR10+")), hdr(hdr10Plus, b4))
        assertEquals(emptyList<Any>(), hdr(hdr10Plus, tablet))
        assertEquals(emptyList<Any>(), hdr(hdr10, b4))
        val sdrOnly = b4.copy(hdr = emptySet())
        assertEquals(listOf(Triple(FindingKind.HdrUnsupported, FindingLevel.Critical, "HDR10+")), hdr(hdr10Plus, sdrOnly))
        assertEquals(listOf(Triple(FindingKind.HdrUnsupported, FindingLevel.Critical, "HDR10")), hdr(hdr10, sdrOnly))
        assertEquals(listOf(Triple(FindingKind.HdrUnsupported, FindingLevel.Warning, "HLG")), hdr(hlg, sdrOnly))
    }

    @Test
    fun theVideosLimits() {
        fun video(video: VideoTech, profile: DeviceProfile = b4) = check.check(sampleTech(video = video), profile, null)
        val av1 = video(sampleVideo(codec = "av1"), b4.copy(videoCodecs = setOf("hevc", "h264")))
        assertEquals(FindingKind.VideoCodec to FindingLevel.Critical, av1.kinds().first())
        assertFalse(av1.videoKept)
        assertTrue(av1.findings.none { it.kind == FindingKind.VideoOk })
        val eightK = video(sampleVideo(width = 7680, height = 4320))
        assertEquals(listOf(3840L, 2160L), eightK.findings.single { it.kind == FindingKind.Resolution }.values)
        assertFalse(eightK.videoKept)
        // a phone's portrait film fits when its sides do, turned
        assertTrue(video(sampleVideo(width = 2160, height = 3840)).findings.none { it.kind == FindingKind.Resolution })
        // 59.94 is 60; 120 is not on a 60 Hz device, a warning
        val sixty = b4.copy(maxFps = 60.0)
        assertTrue(video(sampleVideo(fps = 59.94), sixty).findings.none { it.kind == FindingKind.FrameRate })
        assertEquals(FindingLevel.Warning, video(sampleVideo(fps = 120.0), sixty).findings.single { it.kind == FindingKind.FrameRate }.level)
        assertEquals(listOf(10L), video(sampleVideo(bitDepth = 12)).findings.single { it.kind == FindingKind.BitDepth }.values)
        // the container
        val avi = check.check(sampleTech(format = "avi"), b4.copy(containers = setOf("MKV", "MP4")), null)
        assertEquals(Triple(FindingKind.Container, FindingLevel.Critical, "AVI"), avi.findings.first().let { Triple(it.kind, it.level, it.subject) })
    }

    @Test
    fun audioTracks() {
        fun audio(tracks: List<AudioTech>, profile: DeviceProfile = b4) =
            check.check(sampleTech(audio = tracks), profile, null).findings.filter { it.kind.name.contains("Audio") || it.kind == FindingKind.DtsCore }
                .map { Triple(it.kind, it.level, it.subject) }
        val dtsHd = sampleAudio("dts", "DTS-HD MA", 6, "tur", default = true)
        val eac3 = sampleAudio("eac3", "", 6, "eng")
        // the B4 plays no DTS at all: silent from the start
        assertEquals(listOf(Triple(FindingKind.DefaultAudio, FindingLevel.Critical, "dts tur")), audio(listOf(dtsHd, eac3)))
        // a device that plays the DTS core plays DTS-HD as its core
        val dtsCore = b4.copy(audioCodecs = b4.audioCodecs + "dts")
        assertEquals(listOf(Triple(FindingKind.DtsCore, FindingLevel.Warning, "dts tur")), audio(listOf(dtsHd, eac3), dtsCore))
        // nothing it plays: one finding, about the track it would start with
        assertEquals(
            listOf(Triple(FindingKind.NoAudio, FindingLevel.Critical, "dts tur")),
            audio(listOf(sampleAudio("truehd", "Dolby TrueHD", 8, "eng"), dtsHd)),
        )
        // all of it plays
        assertEquals(listOf(Triple(FindingKind.AudioOk, FindingLevel.Ok, "eac3 eng")), audio(listOf(eac3, sampleAudio("ac3", "", 2, "tur", commentary = true))))
        // no audio, no audio finding
        assertEquals(emptyList<Any>(), audio(emptyList()))
    }

    @Test
    fun subtitles() {
        fun subs(tracks: List<SubtitleTech>, profile: DeviceProfile = b4) =
            check.check(sampleTech(subtitles = tracks), profile, null).findings.filter { it.kind.name.endsWith("Subtitles") }.map { it.kind to it.subject }
        val ass = sampleSubtitle("ass", "tur")
        val vobsub = sampleSubtitle("dvd_subtitle", "eng")
        assertEquals(emptyList<Any>(), subs(listOf(ass)))
        assertEquals(
            listOf(FindingKind.BitmapSubtitles to "dvd_subtitle", FindingKind.TextSubtitles to "ass"),
            subs(listOf(ass, vobsub), b4.copy(subtitleCodecs = setOf("srt"))),
        )
        assertEquals(emptyList<Any>(), subs(listOf(ass, vobsub), tablet))
    }

    @Test
    fun theBitrateAgainstTheThreshold() {
        fun rate(series: IntArray, profile: DeviceProfile = b4): Finding {
            val base = sampleBitrate(series.size)
            val analysis = base.copy(
                total = series,
                peakSecondKbps = series.max(),
                peakFiveSecondsKbps = base.peakOf(series, 5),
            )
            return check.check(sampleTech(), profile, analysis).findings.single { it.kind.name.startsWith("Bitrate") }
        }
        // five seconds over 100 Mbps: it stutters
        val sustained = rate(intArrayOf(80_000, 120_000, 121_000, 119_000, 118_000, 122_000, 70_000))
        assertEquals(FindingKind.BitrateSustained, sustained.kind)
        assertEquals(FindingLevel.Critical, sustained.level)
        assertEquals(listOf(120_000L, 100_000L, 5L), sustained.values)
        // under it
        val fine = rate(intArrayOf(80_000, 90_000, 95_000))
        assertEquals(FindingKind.BitrateOk, fine.kind)
        assertEquals(listOf(95_000L, 100_000L), fine.values)
        // the link limits too: a 240 Mbps USB 2.0 port, the profile itself without a limit
        val usb2Only = b4.copy(peakKbps = 0)
        assertEquals(240_000, usb2Only.limitKbps)
        assertEquals(FindingKind.BitrateOk, rate(intArrayOf(150_000, 160_000), usb2Only).kind)
        // without a limit, no bitrate finding
        assertTrue(check.check(sampleTech(), tablet, sampleBitrate()).findings.none { it.kind.name.startsWith("Bitrate") })
    }

    @Test
    fun anUnfinishedReadingIsNoFinding() {
        val view = sampleTvLabView().copy(bitrate = sampleBitrate(complete = false))
        assertTrue(checkOf(view, names)!!.findings.none { it.kind.name.startsWith("Bitrate") })
        assertEquals(null, checkOf(view.copy(tech = null), names))
    }
}
