package app.nagmedar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-501: routing between the two engines and the hand-over when the engine changes. */
class EngineHostTest {

    private class FakeEngine(override val engineId: EngineId, private val accepts: (EngineMedia) -> Boolean) : PlaybackEngine {
        var playing = false
        var volume = 100
        var rate = 1f
        var stops = 0

        override fun canPlay(media: EngineMedia) = accepts(media)
        override fun play() {
            playing = true
        }

        override fun pause(): Boolean {
            playing = false
            return true
        }

        override fun stop() {
            playing = false
            stops++
        }

        override fun isPlaying() = playing
        override fun isPaused() = !playing
        override fun setTime(time: Long, fast: Boolean) = Unit
        override fun getCurrentTime() = 0L
        override fun getLength() = 0L
        override fun getRate() = rate
        override fun setRate(rate: Float, save: Boolean) {
            this.rate = rate
        }

        override fun getVolume() = volume
        override fun setVolume(volume: Int): Int {
            this.volume = volume
            return 0
        }

        override fun release() = Unit
    }

    private val localFlac = EngineMedia("file:///music/a.flac", isAudio = true, isLocal = true, mimeType = "audio/flac")
    private val stream = EngineMedia("https://radio.example/stream", isAudio = true, isLocal = false)
    private val movie = EngineMedia("file:///movies/b.mkv", isAudio = false, isLocal = true)

    private fun libVlc() = FakeEngine(EngineId.LibVlc) { true }

    private fun audioEngine() = FakeEngine(EngineId.NagmedarAudio) { it.isAudio && it.isLocal }

    @Test
    fun routesLocalMusicToTheAudioEngineAndTheRestToLibVlc() {
        val vlc = libVlc()
        val audio = audioEngine()
        val router = EngineRouter(listOf(audio, vlc))
        assertSame(audio, router.engineFor(localFlac))
        assertSame(vlc, router.engineFor(stream))
        assertSame(vlc, router.engineFor(movie))
    }

    @Test
    fun aStubEngineLeavesEverythingToLibVlc() {
        val vlc = libVlc()
        val stub = FakeEngine(EngineId.NagmedarAudio) { false }
        val host = EngineHost(EngineRouter(listOf(vlc, stub)))
        for (media in listOf(localFlac, stream, movie)) assertSame(vlc, host.prepareFor(media))
        assertEquals(0, vlc.stops)
    }

    @Test
    fun handOverBetweenItems() {
        val vlc = libVlc()
        val audio = audioEngine()
        val changes = mutableListOf<Pair<EngineId, EngineId>>()
        val host = EngineHost(EngineRouter(listOf(audio, vlc))) { from, to -> changes += from to to }
        assertSame(audio, host.current)

        audio.volume = 64
        audio.rate = 1.25f
        audio.play()
        // a stream goes to libVLC: the audio engine stops, libVLC takes its volume and rate
        assertSame(vlc, host.prepareFor(stream))
        assertEquals(EngineId.NagmedarAudio to EngineId.LibVlc, changes.last())
        assertEquals(1, audio.stops)
        assertTrue(!audio.isPlaying())
        assertEquals(64, vlc.volume)
        assertEquals(1.25f, vlc.rate)

        // back to local music: the state flows the other way
        vlc.volume = 30
        assertSame(audio, host.prepareFor(localFlac))
        assertEquals(EngineId.LibVlc to EngineId.NagmedarAudio, changes.last())
        assertEquals(30, audio.volume)

        // same engine again: no hand-over
        val before = changes.size
        host.prepareFor(localFlac)
        assertEquals(before, changes.size)
    }
}
