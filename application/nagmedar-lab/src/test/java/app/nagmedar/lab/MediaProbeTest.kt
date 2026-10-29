package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-802: [MediaProbe] reads the JSON of native/lab/mediaprobe.c. The samples are outputs of the
 * host build (FFmpeg 8.1.3) on the files test-host.sh makes: a renamed key on either side shows
 * up here.
 */
class MediaProbeTest {

    /** AAC with a Turkish SRT and two chapters, as 1.0 probes it (no "video" any more). */
    private val chapters = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":11029,"size":208326,
        "bitrate":151111,"chapters":2,"audio":[{"codec":"aac",
        "profile":"LC","channels":1,"layout":"mono","sample_rate":44100,"bits":0,"bitrate":0,"language":"","title":"",
        "default":true,"forced":false,"commentary":false}],"subtitles":[{"codec":"subrip","language":"tur","title":"",
        "default":true,"forced":false,"hearing_impaired":false}]}"""

    /** In MP4. */
    private val mp4 = """{"version":1,"error":"","format":"mov,mp4,m4a,3gp,3g2,mj2","brand":"isom","duration_ms":11029,"size":179808,
        "bitrate":130425,"chapters":0,"audio":[{"codec":"aac",
        "profile":"LC","channels":1,"layout":"mono","sample_rate":44100,"bits":0,"bitrate":69200,"language":"und",
        "title":"","default":true,"forced":false,"commentary":false}],"subtitles":[]}"""

    /**
     * A film with E-AC-3 5.1, DTS, a TrueHD commentary, FLAC and two subtitle tracks, as a probe before 1.0 cached
     * it: with its "video", which is passed over.
     */
    private val film = """{"version":1,"error":"","format":"matroska,webm","brand":"","duration_ms":3000,"size":4117002,
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

    @Test
    fun chaptersSubtitlesAndTheContainer() {
        val tech = MediaProbe.parse(chapters)!!
        assertEquals("MKV", tech.container)
        assertEquals(2, tech.chapters)
        assertEquals(11029L, tech.durationMs)
        assertEquals("tur", tech.subtitles.single().language)
        assertEquals("aac", tech.audio.single().codec)
        assertEquals("MP4", MediaProbe.parse(mp4)!!.container)
    }

    @Test
    fun aFilmCachedBeforeKeepsEveryAudioTrack() {
        val tech = MediaProbe.parse(film)!!
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
    fun failuresAndOldVersionsAreNotProbes() {
        assertNull(MediaProbe.parse("""{"version":1,"error":"open: No such file or directory"}"""))
        assertNull(MediaProbe.parse(film.replace("\"version\":1", "\"version\":0")))
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
}
