package app.nagmedar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1302: the renderers found, one per device; what a renderer gets; the hand-over between the music engine and libVLC. */
class CastingTest {

    /** A renderer as libVLC reports it: each report is another object, the same native item is equal. */
    private data class Found(val name: String, val type: String, val ref: Int)

    private fun roster() = RendererRoster<Found> { RendererKey.of(it.name, it.type) }

    @Test
    fun theKindsComeFromLibVlcsTypes() {
        assertEquals(RendererKind.Chromecast, RendererKind.of("chromecast"))
        assertEquals(RendererKind.Dlna, RendererKind.of("stream_out_dlna"))
        assertEquals(RendererKind.Other, RendererKind.of("airplay"))
        assertEquals(RendererKind.Other, RendererKind.of(null))
    }

    @Test
    fun aDeviceFoundTwiceIsListedOnce() {
        val roster = roster()
        val v4 = Found("Salon TV", "chromecast", 1)
        val v6 = Found(" salon  tv ", "chromecast", 2)
        assertTrue(roster.add(v4))
        assertFalse(roster.add(v6))
        assertFalse(roster.add(v4))
        assertEquals(listOf(v4), roster.devices)
    }

    @Test
    fun theSameNameOfAnotherKindIsAnotherDevice() {
        val roster = roster()
        val cast = Found("Salon TV", "chromecast", 1)
        val dlna = Found("Salon TV", "stream_out_dlna", 2)
        roster.add(cast)
        assertTrue(roster.add(dlna))
        assertEquals(listOf(cast, dlna), roster.devices)
    }

    @Test
    fun anotherAddressTakesTheShownOnesPlace() {
        val roster = roster()
        val v4 = Found("Salon TV", "chromecast", 1)
        val v6 = Found("Salon TV", "chromecast", 2)
        roster.add(v4)
        roster.add(v6)
        // the second address going changes nothing shown
        assertFalse(roster.remove(v6))
        roster.add(v6)
        // the first going leaves the second in its place
        assertTrue(roster.remove(v4))
        assertEquals(listOf(v6), roster.devices)
        assertTrue(roster.remove(v6))
        assertEquals(emptyList<Found>(), roster.devices)
        assertFalse(roster.remove(v6))
    }

    @Test
    fun devicesKeepTheOrderTheyWereFoundIn() {
        val roster = roster()
        val a = Found("Mutfak", "stream_out_dlna", 1)
        val b = Found("Oturma odası", "chromecast", 2)
        val c = Found("Çalışma odası", "chromecast", 3)
        listOf(a, b, c).forEach { roster.add(it) }
        roster.remove(b)
        roster.add(b)
        assertEquals(listOf(a, c, b), roster.devices)
        roster.clear()
        assertEquals(emptyList<Found>(), roster.devices)
    }

    @Test
    fun whatARendererGets() {
        assertEquals(CastSupport.Sent, CastItem(tracker = false, dsd = false).support)
        assertEquals(CastSupport.AsPcm, CastItem(tracker = false, dsd = true).support)
        assertEquals(CastSupport.LocalOnly, CastItem(tracker = true, dsd = false).support)
        assertTrue(CastHandover.skippedWhileCasting(CastItem(tracker = true, dsd = false)))
        assertFalse(CastHandover.skippedWhileCasting(CastItem(tracker = false, dsd = true)))
    }

    /** The playlist manager as a handover sees it: a queue, the item playing, the engine playing it. */
    private class FakeHost(
        override var engine: EngineId,
        override val item: CastItem? = CastItem(tracker = false, dsd = false),
        override val playing: Boolean = true,
        override val paused: Boolean = !playing,
        private val engineTakesIt: Boolean = true,
    ) : HandoverHost {
        val queue = listOf("a.flac", "b.flac", "c.flac")
        override val index = 1
        override val positionMs = 83_500L
        val replays = mutableListOf<Handover.Replay>()

        override fun engineTakes() = engineTakesIt

        override fun replay(replay: Handover.Replay) {
            replays += replay
            engine = replay.to
        }
    }

    @Test
    fun castingWhileTheEnginePlaysHandsTheTrackToLibVlcWhereItWas() {
        val host = FakeHost(EngineId.NagmedarAudio)
        val handover = CastHandover.change(host, wasCasting = false, casting = true)
        assertEquals(Handover.Replay(EngineId.LibVlc, 1, 83_500L, paused = false), handover)
        assertEquals(listOf(handover), host.replays)
        assertEquals(EngineId.LibVlc, host.engine)
        assertEquals(listOf("a.flac", "b.flac", "c.flac"), host.queue)
    }

    @Test
    fun aPausedTrackStaysPaused() {
        val host = FakeHost(EngineId.NagmedarAudio, playing = false)
        assertEquals(Handover.Replay(EngineId.LibVlc, 1, 83_500L, paused = true), CastHandover.change(host, wasCasting = false, casting = true))
    }

    @Test
    fun castingStoppedGivesTheTrackBackToTheEngine() {
        val host = FakeHost(EngineId.LibVlc)
        assertEquals(Handover.Replay(EngineId.NagmedarAudio, 1, 83_500L, paused = false), CastHandover.change(host, wasCasting = true, casting = false))
        assertEquals(EngineId.NagmedarAudio, host.engine)
    }

    @Test
    fun whatTheEngineDoesNotTakeStaysWithLibVlc() {
        // a film, a stream, music the engine is turned off for
        val host = FakeHost(EngineId.LibVlc, engineTakesIt = false)
        assertEquals(Handover.None, CastHandover.change(host, wasCasting = true, casting = false))
        assertTrue(host.replays.isEmpty())
    }

    @Test
    fun libVlcsItemsChangeRendererWithoutAHandover() {
        val host = FakeHost(EngineId.LibVlc)
        assertEquals(Handover.None, CastHandover.change(host, wasCasting = false, casting = true))
        assertTrue(host.replays.isEmpty())
    }

    @Test
    fun aTrackerModuleStaysOnThisDevice() {
        val host = FakeHost(EngineId.NagmedarAudio, item = CastItem(tracker = true, dsd = false))
        assertEquals(Handover.StaysHere, CastHandover.change(host, wasCasting = false, casting = true))
        assertTrue(host.replays.isEmpty())
        assertEquals(EngineId.NagmedarAudio, host.engine)
    }

    @Test
    fun dsdIsHandedOverToBeSentAsPcm() {
        val host = FakeHost(EngineId.NagmedarAudio, item = CastItem(tracker = false, dsd = true))
        assertEquals(Handover.Replay(EngineId.LibVlc, 1, 83_500L, paused = false), CastHandover.change(host, wasCasting = false, casting = true))
    }

    @Test
    fun nothingPlayingOrNoChangeIsNoHandover() {
        assertEquals(Handover.None, CastHandover.plan(FakeHost(EngineId.NagmedarAudio, playing = false, paused = false), wasCasting = false, casting = true))
        assertEquals(Handover.None, CastHandover.plan(FakeHost(EngineId.NagmedarAudio, item = null), wasCasting = false, casting = true))
        // another renderer chosen while casting: libVLC's already
        assertEquals(Handover.None, CastHandover.plan(FakeHost(EngineId.LibVlc), wasCasting = true, casting = true))
    }
}
