package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-802: [MediaProbe] reads the JSON of native/lab/mediaprobe.c. The samples are real outputs of
 * the host build (FFmpeg 8.1.3, dovi_tool 2.3.4) on the files test-host.sh makes: a renamed key
 * on either side shows up here.
 */
class MediaProbeTest {

    /** dovi_tool's profile 7 MEL test stream with AAC, Turkish SRT and two chapters. */
    private val profile7 = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":11029,"size":208326,
        "bitrate":151111,"chapters":2,"video":{"codec":"hevc","profile":"Main 10","width":256,"height":144,
        "fps":23.976,"bit_depth":10,"transfer":"unknown","primaries":"unknown","matrix":"unknown",
        "max_luminance":1000.0,"min_luminance":0.0001,"max_cll":1000,"max_fall":400,"hdr10plus":false,"dv_profile":7,
        "dv_level":1,"dv_compatibility":6,"dv_el":"MEL","dv_el_present":true,"dv_rpu":true},"audio":[{"codec":"aac",
        "profile":"LC","channels":1,"layout":"mono","sample_rate":44100,"bits":0,"bitrate":0,"language":"","title":"",
        "default":true,"forced":false,"commentary":false}],"subtitles":[{"codec":"subrip","language":"tur","title":"",
        "default":true,"forced":false,"hearing_impaired":false}]}"""

    /** The same stream converted to profile 8.1, in MP4. */
    private val profile81 = """{"version":1,"error":"","format":"mov,mp4,m4a,3gp,3g2,mj2","brand":"isom","duration_ms":11029,"size":179808,
        "bitrate":130425,"chapters":0,"video":{"codec":"hevc","profile":"Main 10","width":256,"height":144,
        "fps":23.9815,"bit_depth":10,"transfer":"unknown","primaries":"unknown","matrix":"unknown",
        "max_luminance":1000.0,"min_luminance":0.0001,"max_cll":1000,"max_fall":400,"hdr10plus":false,"dv_profile":8,
        "dv_level":1,"dv_compatibility":1,"dv_el":"","dv_el_present":false,"dv_rpu":true},"audio":[{"codec":"aac",
        "profile":"LC","channels":1,"layout":"mono","sample_rate":44100,"bits":0,"bitrate":69200,"language":"und",
        "title":"","default":true,"forced":false,"commentary":false}],"subtitles":[]}"""

    /** SDR H.264 1080p with E-AC-3 5.1, DTS, a TrueHD commentary, FLAC and two subtitle tracks. */
    private val sdr = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":3000,"size":4117002,
        "bitrate":10978672,"chapters":0,"video":{"codec":"h264","profile":"Constrained Baseline","width":1920,
        "height":1080,"fps":25.0,"bit_depth":8,"transfer":"unknown","primaries":"unknown","matrix":"unknown",
        "max_luminance":0.0,"min_luminance":0.0,"max_cll":0,"max_fall":0,"hdr10plus":false,"dv_profile":0,
        "dv_level":0,"dv_compatibility":0,"dv_el":"","dv_el_present":false,"dv_rpu":false},"audio":[{"codec":"eac3",
        "profile":"","channels":6,"layout":"5.1(side)","sample_rate":48000,"bits":0,"bitrate":448000,"language":"tur",
        "title":"","default":true,"forced":false,"commentary":false},{"codec":"dts","profile":"DTS","channels":6,
        "layout":"5.1(side)","sample_rate":48000,"bits":0,"bitrate":1411200,"language":"eng","title":"",
        "default":false,"forced":false,"commentary":false},{"codec":"truehd","profile":"","channels":6,
        "layout":"5.1(side)","sample_rate":48000,"bits":24,"bitrate":0,"language":"eng","title":"Yorum",
        "default":false,"forced":false,"commentary":true},{"codec":"flac","profile":"","channels":1,"layout":"mono",
        "sample_rate":96000,"bits":16,"bitrate":0,"language":"jpn","title":"","default":false,"forced":false,
        "commentary":false}],"subtitles":[{"codec":"subrip","language":"tur","title":"","default":true,"forced":false,
        "hearing_impaired":false},{"codec":"subrip","language":"eng","title":"","default":true,"forced":true,
        "hearing_impaired":false}]}"""

    /** HDR10 with ST 2094-40 SEI. */
    private val hdr10plus = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":1001,"size":75755,"bitrate":605434,
        "chapters":0,"video":{"codec":"hevc","profile":"Main 10","width":640,"height":360,"fps":23.976,"bit_depth":10,
        "transfer":"unknown","primaries":"unknown","matrix":"unknown","max_luminance":1000.0,"min_luminance":0.0001,
        "max_cll":1000,"max_fall":400,"hdr10plus":true,"dv_profile":0,"dv_level":0,"dv_compatibility":0,"dv_el":"",
        "dv_el_present":false,"dv_rpu":false},"audio":[],"subtitles":[]}"""

    private val hlg = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":1000,"size":75546,"bitrate":604368,
        "chapters":0,"video":{"codec":"hevc","profile":"Main 10","width":640,"height":360,"fps":25.0,"bit_depth":10,
        "transfer":"arib-std-b67","primaries":"bt2020","matrix":"unknown","max_luminance":0.0,"min_luminance":0.0,
        "max_cll":0,"max_fall":0,"hdr10plus":false,"dv_profile":0,"dv_level":0,"dv_compatibility":0,"dv_el":"",
        "dv_el_present":false,"dv_rpu":false},"audio":[],"subtitles":[]}"""

    @Test
    fun profile7WithItsEnhancementLayer() {
        val tech = MediaProbe.parse(profile7)!!
        assertEquals("MKV", tech.container)
        assertEquals(2, tech.chapters)
        val video = tech.video!!
        assertEquals(DynamicRange.DolbyVision, video.dynamicRange)
        assertEquals(DynamicRange.HDR10, video.baseLayer)
        assertEquals(7, video.dvProfile)
        assertEquals(6, video.dvCompatibility)
        assertEquals("MEL", video.dvEnhancement)
        assertTrue(video.dvEnhancementLayer)
        assertEquals("Main 10", video.profile)
        assertEquals(10, video.bitDepth)
        assertEquals(1000.0, video.maxLuminance, 0.5)
        assertEquals("tur", tech.subtitles.single().language)
        assertEquals("aac", tech.audio.single().codec)
    }

    @Test
    fun profile81InMp4() {
        val tech = MediaProbe.parse(profile81)!!
        assertEquals("MP4", tech.container)
        val video = tech.video!!
        assertEquals(8, video.dvProfile)
        assertEquals(1, video.dvCompatibility)
        assertFalse(video.dvEnhancementLayer)
        assertEquals("", video.dvEnhancement)
    }

    @Test
    fun sdrWithEveryAudioTrack() {
        val tech = MediaProbe.parse(sdr)!!
        val video = tech.video!!
        assertEquals(DynamicRange.SDR, video.dynamicRange)
        assertEquals(Resolution.HD, video.resolution)
        assertEquals(8, video.bitDepth)
        assertEquals(listOf("eac3", "dts", "truehd", "flac"), tech.audio.map { it.codec })
        assertEquals(listOf("tur", "eng", "eng", "jpn"), tech.audio.map { it.language })
        assertEquals(listOf(true, false, false, false), tech.audio.map { it.isDefault })
        assertTrue(tech.audio[2].commentary)
        assertEquals("Yorum", tech.audio[2].title)
        assertEquals(listOf(false, false, true, true), tech.audio.map { it.lossless })
        assertFalse(tech.atmos)
        assertEquals(listOf(false, true), tech.subtitles.map { it.forced })
    }

    @Test
    fun hdr10PlusAndHlg() {
        assertEquals(DynamicRange.HDR10Plus, MediaProbe.parse(hdr10plus)!!.video!!.dynamicRange)
        assertEquals(DynamicRange.HLG, MediaProbe.parse(hlg)!!.video!!.dynamicRange)
    }

    @Test
    fun failuresAndOldVersionsAreNotProbes() {
        assertNull(MediaProbe.parse("""{"version":1,"error":"open: No such file or directory"}"""))
        assertNull(MediaProbe.parse(sdr.replace("\"version\":1", "\"version\":0")))
        assertNull(MediaProbe.parse("not json"))
    }

    @Test
    fun atmosDtsXAndLossless() {
        fun track(codec: String, profile: String) = AudioTech(codec, profile, 8, "7.1", 48000, 24, 0, "eng", "", true, false, false)
        assertTrue(track("truehd", "Dolby TrueHD + Dolby Atmos").atmos)
        assertTrue(track("eac3", "Dolby Digital Plus + Dolby Atmos").atmos)
        assertFalse(track("eac3", "Dolby Digital Plus + Dolby Atmos").lossless)
        assertTrue(track("dts", "DTS-HD MA + DTS:X").dtsX)
        assertTrue(track("dts", "DTS-HD MA + DTS:X").lossless)
        assertFalse(track("dts", "DTS-HD HRA").lossless)
        assertTrue(track("pcm_s24le", "").lossless)
    }

    @Test
    fun resolutionByTheLargerSide() {
        fun res(w: Int, h: Int) = MediaProbe.parse(sdr)!!.video!!.copy(width = w, height = h).resolution
        assertEquals(Resolution.UHD, res(3840, 2160))
        assertEquals(Resolution.UHD, res(3840, 1600))
        assertEquals(Resolution.HD, res(1920, 800))
        assertEquals(Resolution.HD720, res(1280, 536))
        assertEquals(Resolution.SD, res(720, 576))
        assertEquals(Resolution.UHD8K, res(7680, 4320))
    }
}
