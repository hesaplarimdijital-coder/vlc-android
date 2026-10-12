package app.nagmedar.ui.player.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

internal class FakeAudioOutput(var refuses: Boolean = false, var bitstream: Boolean = true) : AudioOutputTarget {
    val calls = mutableListOf<String>()
    var sinks: List<AudioSink> = emptyList()

    override fun apply(mode: AudioOutputMode, codecs: Set<PassthroughCodec>): Boolean {
        calls += mode.key + ":" + PassthroughCodec.encode(codecs)
        return !refuses
    }

    override fun canPassthrough(): Boolean = bitstream

    override fun sinks(): List<AudioSink> = sinks
}

/**
 * S-508: how the sound leaves, chosen and kept for every film; the codecs forced as a bitstream;
 * the player told at once and again as a film starts; what is plugged in read on demand; a refusal
 * shown.
 */
@RunWith(AndroidJUnit4::class)
class AudioOutputTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("audio-output-test", Context.MODE_PRIVATE)
    private val store = PrefsAudioOutputStore(prefs)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun theChoiceReachesThePlayerAndTheSettings() {
        val target = FakeAudioOutput()
        val output = AudioOutputController(target, store)
        assertEquals(AudioOutputMode.Auto, output.mode)
        output.choose(AudioOutputMode.Passthrough)
        assertEquals(listOf("passthrough:"), target.calls)
        assertEquals(AudioOutputMode.Passthrough, store.mode())
        assertFalse(output.refused)
        // forced: the usual codecs first, then the ones ticked
        output.choose(AudioOutputMode.Forced)
        assertEquals("forced:ac3,eac3,dts", target.calls.last())
        output.toggle(PassthroughCodec.TrueHd)
        output.toggle(PassthroughCodec.Dts)
        assertEquals("forced:ac3,eac3,truehd", target.calls.last())
        assertEquals(setOf(PassthroughCodec.Ac3, PassthroughCodec.Eac3, PassthroughCodec.TrueHd), store.codecs())
        // the same choice again is nothing
        target.calls.clear()
        output.choose(AudioOutputMode.Forced)
        assertEquals(emptyList<String>(), target.calls)
        // a film starts: the player is told again
        output.onMediaStarted()
        assertEquals(listOf("forced:ac3,eac3,truehd"), target.calls)
        // the settings come back
        val again = AudioOutputController(FakeAudioOutput(), store)
        assertEquals(AudioOutputMode.Forced, again.mode)
        assertEquals(store.codecs(), again.codecs)
    }

    @Test
    fun aRefusalAndWhatIsPluggedInShow() {
        val target = FakeAudioOutput(refuses = true, bitstream = false)
        target.sinks = listOf(AudioSink("LG TV", AudioSinkKind.Hdmi, setOf(PassthroughCodec.Ac3, PassthroughCodec.Eac3)), AudioSink("Hoparlör", AudioSinkKind.Speaker, emptySet()))
        val output = AudioOutputController(target, store)
        output.refresh()
        assertEquals(2, output.sinks.size)
        assertFalse(output.canPassthrough)
        output.choose(AudioOutputMode.Stereo)
        assertTrue(output.refused)
        target.refuses = false
        output.choose(AudioOutputMode.Multichannel)
        assertFalse(output.refused)
        // in Auto, a film's start changes nothing
        target.calls.clear()
        output.choose(AudioOutputMode.Auto)
        target.calls.clear()
        output.onMediaStarted()
        assertEquals(emptyList<String>(), target.calls)
    }

    @Test
    fun codecsAreNamedByAndroidsEncodings() {
        assertEquals(PassthroughCodec.TrueHd, PassthroughCodec.of(14))
        assertEquals(null, PassthroughCodec.of(2))
        assertEquals(setOf(PassthroughCodec.Ac3, PassthroughCodec.DtsHd), PassthroughCodec.parse("ac3, dtshd,mp3"))
        assertEquals("", PassthroughCodec.encode(emptySet()))
        assertEquals(AudioOutputMode.Auto, AudioOutputMode.of("whatever"))
    }
}
