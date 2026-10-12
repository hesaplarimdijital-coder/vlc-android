package app.nagmedar.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-906: the DSP settings as saved, as handed to the engine, and as imported from AutoEq. */
@RunWith(AndroidJUnit4::class)
class DspSettingsTest {

    private val sample = DspSettings(
        preampDb = -2.5,
        autoPreamp = false,
        graphicEnabled = true,
        graphicDb = List(NativeAudio.GRAPHIC_BANDS) { if (it == 3) 4.0 else if (it == 12) -6.0 else 0.0 },
        parametricEnabled = true,
        parametric = listOf(EqBand(1000.0, 6.0, 1.2), EqBand(120.0, -3.0, 0.7, EqType.LowShelf), EqBand(30.0, 0.0, 0.7, EqType.HighPass)),
        profileEnabled = true,
        profileName = "Sennheiser HD 600",
        profilePreampDb = -1.5,
        profile = listOf(EqBand(3300.0, -4.0, 2.0)),
        limiterEnabled = true,
        limiterThresholdDb = -0.5,
        limiterReleaseMs = 80.0,
        limiterLookaheadMs = 4.0,
    )

    @Test
    fun jsonRoundTrip() {
        val back = DspSettings.fromJson(sample.toJson())
        assertEquals(sample, back)
        assertEquals(DspSettings(), DspSettings.fromJson(null))
        assertEquals(DspSettings(), DspSettings.fromJson("not json"))
        assertEquals(DspSettings(), DspSettings.fromJson("{}"))
    }

    @Test
    fun identity() {
        assertTrue(DspSettings().identity)
        assertTrue(DspSettings(graphicEnabled = true, parametricEnabled = true, parametric = listOf(EqBand(1000.0, 0.0))).identity)
        assertTrue(sample.copy(bypass = true).identity)
        assertFalse(sample.identity)
        assertFalse(DspSettings(preampDb = -3.0).identity)
        assertFalse(DspSettings(limiterEnabled = true).identity)
        assertFalse(DspSettings(parametricEnabled = true, parametric = listOf(EqBand(30.0, type = EqType.HighPass))).identity)
        // a disabled stage with content in it does nothing
        assertTrue(DspSettings(graphicDb = List(NativeAudio.GRAPHIC_BANDS) { 3.0 }).identity)
    }

    @Test
    fun flatForTheEngine() {
        val flags = sample.flags
        val values = sample.values
        assertEquals(NativeAudio.DSP_FLAGS, flags.size)
        assertEquals(NativeAudio.DSP_VALUES, values.size)
        assertEquals(listOf(0, 0, 1, 1, 1, 1), flags.take(6))
        // the second parametric band: enabled, a low shelf
        assertEquals(1, flags[6 + 1 * 2])
        assertEquals(NativeAudio.EQ_LOW_SHELF, flags[6 + 1 * 2 + 1])
        // the fourth parametric band is not there
        assertEquals(0, flags[6 + 3 * 2])
        // the first profile band
        assertEquals(1, flags[6 + NativeAudio.EQ_BANDS * 2])
        assertEquals(-2.5, values[0], 0.0)
        assertEquals(-1.5, values[1], 0.0)
        assertEquals(-0.5, values[2], 0.0)
        assertEquals(80.0, values[3], 0.0)
        assertEquals(4.0, values[4], 0.0)
        assertEquals(4.0, values[5 + 3], 0.0)
        assertEquals(-6.0, values[5 + 12], 0.0)
        val second = 5 + NativeAudio.GRAPHIC_BANDS + 1 * 3
        assertEquals(120.0, values[second], 0.0)
        assertEquals(-3.0, values[second + 1], 0.0)
        assertEquals(0.7, values[second + 2], 0.0)
        val profile = 5 + NativeAudio.GRAPHIC_BANDS + NativeAudio.EQ_BANDS * 3
        assertEquals(3300.0, values[profile], 0.0)
    }

    @Test
    fun autoEqImport() {
        val text = """
            Preamp: -6.4 dB
            Filter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 0.70
            Filter 2: ON LSC Fc 105 Hz Gain 2.0 dB Q 0.70
            Filter 3: OFF HSC Fc 10000 Hz Gain -4.5 dB Q 0.70
            Filter 4: ON PK Fc 3300 Hz Gain -1.1 dB Q 2.54
        """.trimIndent()
        val (preamp, bands) = DspSettings.parseAutoEq(text)!!
        assertEquals(-6.4, preamp, 0.0)
        assertEquals(4, bands.size)
        assertEquals(EqBand(105.0, -3.2, 0.7), bands[0])
        assertEquals(EqType.LowShelf, bands[1].type)
        assertEquals(EqType.HighShelf, bands[2].type)
        assertFalse(bands[2].enabled)
        assertEquals(2.54, bands[3].q, 0.0)
        assertNull(DspSettings.parseAutoEq("no filters here"))
        // more than ten filters: the first ten
        val many = (1..14).joinToString("\n") { "Filter $it: ON PK Fc ${it * 100} Hz Gain 1.0 dB Q 1.00" }
        assertEquals(NativeAudio.EQ_BANDS, DspSettings.parseAutoEq(many)!!.second.size)
        assertNotNull(DspSettings.parseAutoEq("Filter 1: ON HP Fc 20 Hz"))
    }

    @Test
    fun headphoneProfiles() {
        val text = "Preamp: -6.4 dB\nFilter 1: ON PK Fc 105 Hz Gain -3.2 dB Q 0.70\nFilter 2: ON HSC Fc 10000 Hz Gain 2.0 dB Q 0.70\n"
        val named = HeadphoneProfile.parse(text, "Sennheiser HD 600 ParametricEQ.txt", "Pasted")!!
        assertEquals("Sennheiser HD 600", named.name)
        assertEquals(-6.4, named.preampDb, 0.0)
        assertEquals(2, named.bands.size)
        assertEquals("Pasted", HeadphoneProfile.parse(text, null, "Pasted")!!.name)
        assertEquals("AirPods Pro", HeadphoneProfile.parse(text, "content/AirPods Pro_ParametricEQ.txt", "Pasted")!!.name)
        assertNull(HeadphoneProfile.parse("nothing", "x.txt", "Pasted"))
        val list = listOf(named, named.copy(name = "Other"))
        assertEquals(list, HeadphoneProfile.listFromJson(HeadphoneProfile.listToJson(list)))
        assertTrue(HeadphoneProfile.listFromJson("broken").isEmpty())
        val chain = DspSettings().withProfile(named)
        assertTrue(chain.profileEnabled)
        assertEquals("Sennheiser HD 600", chain.profileName)
        assertEquals(-6.4, chain.profilePreampDb, 0.0)
        assertFalse(chain.identity)
    }

    @Test
    fun theMeter() {
        val m = DspMeter(frames = 48_000, clipped = 12, peak = 0.5, reductionDb = 2.0)
        assertTrue(m.clipping)
        assertEquals(-6.02, m.peakDb, 0.01)
        assertEquals(Double.NEGATIVE_INFINITY, DspMeter(0, 0, 0.0, 0.0).peakDb, 0.0)
    }

    @Test
    fun graphicFrequenciesMatchTheEngine() {
        assertEquals(NativeAudio.GRAPHIC_BANDS, DspSettings.graphicFrequencies.size)
        assertEquals(25.0, DspSettings.graphicFrequencies.first(), 0.0)
        assertEquals(18000.0, DspSettings.graphicFrequencies.last(), 0.0)
        // half-octave spacing, give or take the ISO rounding
        DspSettings.graphicFrequencies.zipWithNext().forEach { (a, b) -> assertEquals(Math.sqrt(2.0), b / a, 0.03) }
    }
}
