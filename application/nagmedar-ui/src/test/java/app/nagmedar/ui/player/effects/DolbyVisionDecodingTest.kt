package app.nagmedar.ui.player.effects

import app.nagmedar.lab.MediaProbe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-507: only a Dolby Vision profile 5 film, whose base layer is not HDR10, goes to the software decoder. */
class DolbyVisionDecodingTest {

    private fun probe(dvProfile: Int, elPresent: Boolean = false) = MediaProbe.parse(
        """{"version":1,"error":"","format":"mov,mp4","brand":"isom","duration_ms":11029,"size":179808,
        "bitrate":130425,"chapters":0,"video":{"codec":"hevc","profile":"Main 10","width":3840,"height":2160,
        "fps":23.976,"bit_depth":10,"transfer":"smpte2084","primaries":"bt2020","matrix":"bt2020nc",
        "max_luminance":1000.0,"min_luminance":0.0001,"max_cll":1000,"max_fall":400,"hdr10plus":false,"dv_profile":$dvProfile,
        "dv_level":6,"dv_compatibility":0,"dv_el":"","dv_el_present":$elPresent,"dv_rpu":true},"audio":[],"subtitles":[]}""",
    )!!

    @Test
    fun profileFiveAlone() {
        assertTrue(DolbyVisionDecoding.needsSoftware(probe(5)))
        assertFalse(DolbyVisionDecoding.needsSoftware(probe(7, elPresent = true)))
        assertFalse(DolbyVisionDecoding.needsSoftware(probe(8)))
        assertFalse(DolbyVisionDecoding.needsSoftware(probe(0)))
        assertFalse(DolbyVisionDecoding.decidedFor(null))
        assertFalse(DolbyVisionDecoding.decidedFor("file:///films/none.mkv"))
    }
}
