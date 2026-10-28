package org.videolan.vlc.media

import java.util.concurrent.atomic.AtomicInteger

/**
 * Nağmedar's podcast episodes (S-1104) follow their show's own resume rule (where they were left, or after the show's
 * intro), set by the player that starts them: VLC's classic "resume playback" setting (ask, always, never) does not
 * apply to them, and its dialog never shows before one. Kept by the new interface's module, which this one does not
 * see, whose observer of the playback service ([PlaybackObserver]) sets [owner] while the service lives.
 */
fun interface ResumeOwner {
    /** Whether the media at [location] is an episode of a subscribed show. Called off the main thread. */
    fun ownsResume(location: String): Boolean
}

/**
 * A show's volume boost (S-1104) is the engine's volume raised above the listener's: [restore] puts it back. The playlist
 * calls it before any other media starts, so that nothing but the episode ever plays boosted. While a boost holds the
 * engine, the volume the user sees and sets is still their own ([listenerVolume], [engineVolume]): the boosted one is
 * never kept as theirs.
 */
fun interface VolumeRestorer {
    /** Gives the engines the volume the listener set. Called on the main thread; cheap when nothing is boosted. */
    fun restore()

    /** The listener's own volume while the engine plays at [engine]: without the boost, while one holds it. */
    fun listenerVolume(engine: Int): Int = engine

    /** The listener sets [volume]: the engine's volume for it, raised by the boost that holds it now (which goes on over [volume]). */
    fun engineVolume(volume: Int): Int = volume
}

/**
 * libVLC ends a stream, or fails on it, when its connection drops (the network lost, Wi-Fi handed over to mobile data)
 * as it does at the media's real end. The guard, kept by the new interface's podcast observer, tells one from the other
 * for the media it knows (podcast episodes, live streams such as radio): when it [takesOver], the playlist neither takes
 * the media as ended nor goes on, and the guard opens it again where it was ([PlaylistManager.reopenCurrent]). A stream
 * that stalls it stops itself ([PlaylistManager.dropStalled]). Called on the main thread; never blocks.
 */
interface DropoutGuard {
    /** libVLC ended ([error] false) or failed on the media at [location], last at [timeMs] of [lengthMs] (0: unknown). */
    fun takesOver(location: String, timeMs: Long, lengthMs: Long, error: Boolean): Boolean

    /** The playlist stops: a media waiting to be opened again is left. */
    fun stopped()

    /** Whether a media is waiting to be opened again: the service stays in the foreground, the Wi-Fi awake. */
    val recovering: Boolean
}

object ResumeHook {
    @Volatile
    var owner: ResumeOwner? = null

    @Volatile
    var volume: VolumeRestorer? = null

    @Volatile
    var dropout: DropoutGuard? = null

    private val halts = LinkedHashMap<String, Int>()
    private var haltCount = 0

    /** libVLC's playback of the media at [location] ended, failed or was stopped. */
    @Synchronized
    fun halted(location: String) {
        halts.remove(location)
        halts[location] = ++haltCount
        if (halts.size > KEPT_HALTS) halts.remove(halts.keys.first())
    }

    /** A number that grows each time the media at [location] halts ([halted]); 0 before the first. */
    @Synchronized
    fun haltStamp(location: String): Int = halts[location] ?: 0

    private const val KEPT_HALTS = 8

    private val changing = AtomicInteger()

    /** Another media is being started: the volume is given back first, and no boost is applied until [mediaChanged]. */
    fun mediaChanging() {
        changing.incrementAndGet()
        volume?.restore()
    }

    fun mediaChanged() {
        if (changing.decrementAndGet() < 0) changing.set(0)
    }

    /** Whether a media is being started now. */
    val switching: Boolean get() = changing.get() > 0
}
