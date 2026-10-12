package app.nagmedar.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-906: the response the equaliser screen draws is the engine's: the figures here come from
 * buildsystem/nagmedar-native/dsp_model.py, which the host tests hold the engine's output to.
 */
class DspResponseTest {

    private fun graphic(vararg db: Double) = List(NativeAudio.GRAPHIC_BANDS) { i -> db.getOrElse(i) { 0.0 } }

    @Test
    fun theModelsResponse() {
        // dsp_model.py --response 48000 HZ --graphic 3,-2,0,4,0,0,-6,0,0,2,0,0,5,0,0,-3,0,0,1,2
        //   --peq "pk:1000:6:1.2;ls:120:-3:0.7;hs:8000:2.5:0.7;hp:30:0:0.7" --profile "pk:3300:-4:2;lp:16000:0:0.7"
        val settings = DspSettings(
            graphicEnabled = true,
            graphicDb = graphic(3.0, -2.0, 0.0, 4.0, 0.0, 0.0, -6.0, 0.0, 0.0, 2.0, 0.0, 0.0, 5.0, 0.0, 0.0, -3.0, 0.0, 0.0, 1.0, 2.0),
            parametricEnabled = true,
            parametric = listOf(
                EqBand(1000.0, 6.0, 1.2),
                EqBand(120.0, -3.0, 0.7, EqType.LowShelf),
                EqBand(8000.0, 2.5, 0.7, EqType.HighShelf),
                EqBand(30.0, 0.0, 0.7, EqType.HighPass),
            ),
            profileEnabled = true,
            profile = listOf(EqBand(3300.0, -4.0, 2.0), EqBand(16000.0, 0.0, 0.7, EqType.LowPass)),
            autoPreamp = false,
        )
        val biquads = DspResponse.biquads(settings, 48_000)
        assertEquals(9 + 4 + 2, biquads.size)
        val expected = mapOf(50.0 to -2.992826, 120.0 to -1.692307, 1000.0 to 6.573271, 3300.0 to -3.914908, 8000.0 to 0.964395, 16000.0 to -0.126516)
        for ((hz, db) in expected) assertEquals("at $hz Hz", db, DspResponse.eqDb(biquads, hz, 48_000), 1e-5)
    }

    @Test
    fun theAutomaticPreamp() {
        // dsp_model.py's chain(--peq "pk:1000:6:1.2;ls:120:-3:0.7" --graphic 3,0,0,4 --auto-preamp) at 48 kHz
        val settings = DspSettings(
            autoPreamp = true,
            graphicEnabled = true,
            graphicDb = graphic(3.0, 0.0, 0.0, 4.0),
            parametricEnabled = true,
            parametric = listOf(EqBand(1000.0, 6.0, 1.2), EqBand(120.0, -3.0, 0.7, EqType.LowShelf)),
        )
        assertEquals(-6.001268727, DspResponse.autoPreampDb(settings, 48_000), 1e-6)
        // the whole curve peaks at 0 dB with it
        val curve = DspResponse.curve(settings, DspResponse.logFrequencies(400))
        assertEquals(0.0, curve.max(), 0.01)
        // a cut alone takes nothing back
        assertEquals(0.0, DspResponse.autoPreampDb(DspSettings(autoPreamp = true, parametricEnabled = true, parametric = listOf(EqBand(1000.0, -6.0))), 48_000), 0.0)
    }

    @Test
    fun theGainsAndTheBypass() {
        val settings = DspSettings(autoPreamp = false, preampDb = -3.0, profileEnabled = true, profilePreampDb = -1.5)
        assertEquals(-4.5, DspResponse.gainDb(settings, 48_000), 1e-12)
        assertTrue(DspResponse.curve(settings, listOf(100.0, 1000.0)).all { kotlin.math.abs(it + 4.5) < 1e-9 })
        val bypassed = settings.copy(bypass = true, graphicEnabled = true, graphicDb = graphic(6.0))
        assertTrue(DspResponse.curve(bypassed, listOf(25.0, 1000.0)).all { it == 0.0 })
    }

    @Test
    fun bandsBeyondTheOutputDoNothing() {
        assertNull(DspResponse.biquad(EqBand(23_000.0, 6.0), 44_100))
        assertNull(DspResponse.biquad(EqBand(1000.0, 0.0), 44_100))
        assertNull(DspResponse.biquad(EqBand(1000.0, 6.0, enabled = false), 44_100))
        // a peak's gain at its centre is its own
        assertEquals(6.0, DspResponse.eqDb(listOfNotNull(DspResponse.biquad(EqBand(1000.0, 6.0, 1.0), 48_000)), 1000.0, 48_000), 1e-9)
    }

    @Test
    fun vlcsPresetsOnTwentyBands() {
        assertEquals(18, GraphicPresets.keys.size)
        assertEquals(List(NativeAudio.GRAPHIC_BANDS) { 0.0 }, GraphicPresets.bands("flat"))
        val rock = GraphicPresets.bands("rock")!!
        assertEquals(8.0, rock.first(), 0.0) // under VLC's 60 Hz band: its value
        assertEquals(11.0, rock.last(), 0.0) // over 16 kHz: 11.2, to the half decibel
        assertEquals(-2.5, rock[11], 0.0) // 1120 Hz, between 1 kHz's -3.2 and 3 kHz's 4.0
        assertEquals("rock", GraphicPresets.keyOf(rock))
        assertNull(GraphicPresets.keyOf(rock.mapIndexed { i, db -> if (i == 0) db + 0.5 else db }))
        assertNull(GraphicPresets.bands("nope"))
    }
}
