package app.nagmedar.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-910: the signal path model over what the engine prints (a real print of nagaudio's). */
@RunWith(AndroidJUnit4::class)
class SignalPathTest {

    private val playing = """{"version": "0.1.0", "state": 1, "source": {"id": 1, "url": "t/tagged_flac16.flac", "codec": "flac", "container": "flac", "rate": 44100, "channels": 2, "bits": 16, "float": false, "duration_ms": 4000, "bitrate": 531610}, "output": {"sink": "pull", "rate": 44100, "channels": 2, "format": "s16", "bits": 16, "exclusive": false}, "volume": 1.0, "crossfade_ms": 0, "replaygain": {"mode": "track", "preamp_db": 0.0, "gain_db": -3.25, "tagged": true, "clipping_prevented": false}, "tempo": 1.25, "pitch": 0.0, "balance": 0.0, "mono": false, "dsp": [{"stage": "preamp", "db": -2.0}, {"stage": "graphic_eq", "bands": 2}, {"stage": "limiter", "db": -1.0, "lookahead_ms": 5.0, "release_ms": 100}], "dsp_active": true, "dsp_level_db": -2.94, "ab_side": "A", "ab_gain_db": 0.0, "bit_perfect": false, "not_bit_perfect_because": "replaygain"}"""

    private val perfect = """{"version":"0.1.0","state":1,"source":{"id":7,"url":"/a.flac","codec":"flac","container":"flac","rate":96000,"channels":2,"bits":24,"float":false,"duration_ms":3000,"bitrate":2110618},"output":{"sink":"usb","rate":96000,"channels":2,"format":"s32","bits":32,"exclusive":true},"volume":1.0000,"crossfade_ms":0,"replaygain":{"mode":"off","preamp_db":0.00,"gain_db":0.00,"tagged":false,"clipping_prevented":false},"tempo":1.0000,"pitch":0.00,"balance":0.00,"mono":false,"dsp":[],"dsp_active":false,"dsp_level_db":0.00,"ab_side":"A","ab_gain_db":0.00,"bit_perfect":true,"not_bit_perfect_because":""}"""

    private val dsd = """{"version":"0.1.0","state":1,"source":{"id":2,"url":"/x.dsf","codec":"dsd_lsbf_planar","container":"dsf","rate":176400,"channels":2,"bits":24,"float":false,"duration_ms":499,"bitrate":5644800,"dsd_rate":2822400,"dop":true},"output":{"sink":"pull","rate":176400,"channels":2,"format":"s32","bits":32,"exclusive":false},"volume":1.0000,"crossfade_ms":0,"replaygain":{"mode":"off","preamp_db":0.00,"gain_db":0.00,"tagged":false,"clipping_prevented":false},"tempo":1.0000,"pitch":0.00,"balance":0.00,"mono":false,"dsp":[],"dsp_active":false,"dsp_level_db":0.00,"ab_side":"B","ab_gain_db":1.50,"bit_perfect":false,"not_bit_perfect_because":"output"}"""

    @Test
    fun theStagesAndTheReason() {
        val p = SignalPath.parse(playing)!!
        assertEquals(EngineState.Playing, p.state)
        assertEquals("flac", p.source!!.codec)
        assertEquals(16, p.source!!.bits)
        assertNull(p.source!!.dsdName)
        assertEquals("pull", p.output!!.sink)
        assertFalse(p.output!!.exclusive)
        assertEquals(ReplayGainMode.Track, p.replayGain.mode)
        assertEquals(-3.25, p.replayGain.gainDb, 0.0)
        assertTrue(p.replayGain.tagged)
        assertEquals(1.25, p.tempo, 0.0)
        assertEquals(listOf("preamp", "graphic_eq", "limiter"), p.dsp.map { it.name })
        assertEquals(2, p.dsp[1].bands)
        assertEquals(-1.0, p.dsp[2].db!!, 0.0)
        assertEquals(5.0, p.dsp[2].lookaheadMs!!, 0.0)
        assertTrue(p.dspActive)
        assertEquals(-2.94, p.dspLevelDb, 0.0)
        assertEquals(AbSide.A, p.abSide)
        assertFalse(p.bitPerfect)
        assertEquals(SignalPath.Reason.ReplayGain, p.reason)
        assertEquals(listOf("replaygain", "tempo", "preamp", "graphic_eq", "limiter"), p.activeStages)
    }

    @Test
    fun bitPerfectOnTheUsbOutput() {
        val p = SignalPath.parse(perfect)!!
        assertTrue(p.bitPerfect)
        assertEquals(SignalPath.Reason.None, p.reason)
        assertEquals("usb", p.output!!.sink)
        assertTrue(p.output!!.exclusive)
        assertTrue(p.activeStages.isEmpty())
        assertFalse(p.replayGain.active)
    }

    @Test
    fun dsdAsDop() {
        val p = SignalPath.parse(dsd)!!
        assertEquals("DSD64", p.source!!.dsdName)
        assertTrue(p.source!!.dop)
        assertEquals(SignalPath.Reason.Output, p.reason)
        assertEquals(AbSide.B, p.abSide)
        assertEquals(1.5, p.abGainDb, 0.0)
    }

    @Test
    fun whatIsNotAPath() {
        assertNull(SignalPath.parse("{}"))
        assertNull(SignalPath.parse("nope"))
        assertEquals(SignalPath.Reason.Output, SignalPath.Reason.of("something new"))
    }
}
