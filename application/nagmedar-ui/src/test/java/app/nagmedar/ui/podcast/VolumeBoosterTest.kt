package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S-1104: a show's volume boost plays over the listener's own volume and gives it back: what the listener sets while it
 * holds the engine is theirs (never the boosted volume), and a fade or the system's ducking meanwhile does not keep the
 * next media boosted.
 */
class VolumeBoosterTest {

    private class Engine(var volume: Int = 100)

    private val engine = Engine()
    private val booster = VolumeBooster<Engine>({ it.volume }, { e, volume -> e.volume = volume })

    /** What the playback service does when the listener sets [volume]: the engine at the boost's level for it. */
    private fun listenerSets(volume: Int) {
        engine.volume = booster.listenerSets(volume)
    }

    @Test
    fun anEpisodePlaysBoostedAndTheNextMediaAtTheListenersVolume() {
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        assertEquals(170, engine.volume)
        assertEquals(100, booster.listenerVolume(engine.volume))
        booster.end()
        assertEquals(100, engine.volume)
        assertEquals(100, booster.listenerVolume(engine.volume))
    }

    @Test
    fun aVolumeSetDuringTheBoostIsTheListenersOwn() {
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        // Volume+ from what the bars show, the listener's 100: the episode goes on boosted over 105
        listenerSets(booster.listenerVolume(engine.volume) + 5)
        assertEquals(178, engine.volume)
        assertEquals(105, booster.listenerVolume(engine.volume))
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        assertEquals(178, engine.volume)
        // the music that follows plays at 105, never at the boosted volume
        booster.end()
        assertEquals(105, engine.volume)
    }

    @Test
    fun aMuteDuringTheBoostStaysAfterIt() {
        booster.update(engine, episodeId = 1, boost = VolumeBoost.LIGHT)
        assertEquals(130, engine.volume)
        listenerSets(0)
        assertEquals(0, engine.volume)
        listenerSets(100)
        assertEquals(130, engine.volume)
        listenerSets(0)
        booster.update(engine, episodeId = null, boost = VolumeBoost.OFF)
        assertEquals(0, engine.volume)
    }

    @Test
    fun aDuckingMeanwhileNeverLeavesTheNextMediaBoosted() {
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        // a notification ducks the episode; the boost waits for the volume to come back
        engine.volume = 56
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        assertEquals(56, engine.volume)
        // the ducking gives the listener's volume back, the boost over it
        listenerSets(100)
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        assertEquals(170, engine.volume)
        // ducked again as the episode ends: the next media plays at the listener's volume
        engine.volume = 56
        booster.end()
        assertEquals(100, engine.volume)
    }

    @Test
    fun aChangeOfTheShowsStepReachesTheEpisodePlaying() {
        booster.update(engine, episodeId = 1, boost = VolumeBoost.LIGHT)
        assertEquals(130, engine.volume)
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        assertEquals(170, engine.volume)
        booster.update(engine, episodeId = 1, boost = VolumeBoost.OFF)
        assertEquals(100, engine.volume)
    }

    @Test
    fun theVolumeGoesBackWhenAnotherEngineTakesOver() {
        val music = Engine(volume = 170)
        booster.update(engine, episodeId = 1, boost = VolumeBoost.STRONG)
        booster.update(music, episodeId = null, boost = VolumeBoost.OFF)
        assertEquals(100, engine.volume)
        assertEquals(100, music.volume)
    }

    @Test
    fun withoutABoostTheListenersVolumeIsTheEngines() {
        assertEquals(120, booster.listenerSets(120))
        assertEquals(120, booster.listenerVolume(120))
        booster.update(engine, episodeId = 2, boost = VolumeBoost.OFF)
        booster.end()
        assertEquals(100, engine.volume)
    }
}
