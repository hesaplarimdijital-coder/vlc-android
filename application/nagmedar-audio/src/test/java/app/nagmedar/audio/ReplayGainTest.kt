package app.nagmedar.audio

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** S-907: the scanner's results and the tags as the Kotlin side carries them (the engine itself is tested on the host). */
@RunWith(AndroidJUnit4::class)
class ReplayGainTest {

    @Test
    fun sourceInfoCarriesTheTags() {
        val tagged = SourceInfo.parse(
            """{"codec":"flac","container":"flac","rate":44100,"channels":2,"bits":16,"float":false,"duration_ms":4000,"bitrate":539530,"track_gain_db":-3.25,"track_peak":0.987654,"album_gain_db":-4.50,"album_peak":0.999000}""",
        )!!
        assertTrue(tagged.hasReplayGain)
        assertEquals(-3.25, tagged.trackGainDb!!, 0.0)
        assertEquals(0.987654, tagged.trackPeak!!, 1e-9)
        assertEquals(-4.5, tagged.albumGainDb!!, 0.0)
        val plain = SourceInfo.parse("""{"codec":"flac","container":"flac","rate":44100,"channels":2,"bits":16,"float":false,"duration_ms":4000,"bitrate":539530}""")!!
        assertFalse(plain.hasReplayGain)
        assertNull(plain.trackGainDb)
    }

    @Test
    fun sourceInfoKnowsDsd() {
        val dsd = SourceInfo.parse("""{"codec":"dsd_lsbf_planar","container":"dsf","rate":352800,"channels":2,"bits":0,"float":true,"duration_ms":499,"bitrate":5644800,"dsd_rate":2822400}""")!!
        assertTrue(dsd.isDsd)
        assertEquals("DSD64", dsd.dsdName)
        assertFalse(dsd.dop)
        val dop = SourceInfo.parse("""{"codec":"dsd_msbf","container":"iff","rate":352800,"channels":2,"bits":24,"float":false,"duration_ms":499,"bitrate":5644800,"dsd_rate":11289600,"dop":true}""")!!
        assertTrue(dop.dop)
        assertEquals("DSD256", dop.dsdName)
        val pcm = SourceInfo.parse("""{"codec":"flac","container":"flac","rate":44100,"channels":2,"bits":16,"float":false,"duration_ms":4000,"bitrate":539530}""")!!
        assertFalse(pcm.isDsd)
        assertNull(pcm.dsdName)
    }

    @Test
    fun loudnessOfTheEnginesArray() {
        val l = Loudness.of(doubleArrayOf(-23.04, 0.0705, 0.0704, 5.04, 240000.0, 48000.0))!!
        assertEquals(-23.04, l.lufs, 0.0)
        assertEquals(240000L, l.frames)
        assertEquals(48000, l.rate)
        assertFalse(l.silent)
        assertTrue(Loudness.of(doubleArrayOf(Double.NEGATIVE_INFINITY, 0.0, 0.0, 0.0, 10.0, 48000.0))!!.silent)
        assertNull(Loudness.of(null))
        assertNull(Loudness.of(doubleArrayOf(1.0)))
    }

    @Test
    fun theDefaultsLeaveTheSamplesAlone() {
        val s = ReplayGainSettings()
        assertEquals(ReplayGainMode.Off, s.mode)
        assertEquals(0.0, s.preampDb, 0.0)
        assertTrue(s.preventClipping)
        assertEquals(NativeAudio.REPLAYGAIN_TRACK, ReplayGainMode.Track.ordinal)
        assertEquals(NativeAudio.REPLAYGAIN_ALBUM, ReplayGainMode.Album.ordinal)
    }

    @Test
    fun displayNameOfAFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Gece Mavisi.opus", LoudnessScanner.displayName(context, Uri.parse("file:///storage/emulated/0/Music/Gece%20Mavisi.opus")))
        assertEquals("", LoudnessScanner.displayName(context, Uri.parse("file:///")))
    }
}
