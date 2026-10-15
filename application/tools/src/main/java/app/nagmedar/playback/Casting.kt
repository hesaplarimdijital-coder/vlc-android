package app.nagmedar.playback

import java.util.Locale

/**
 * S-1302: what a renderer is, by the type libVLC's renderer discovery gives it: a Chromecast
 * (microdns_renderer, "chromecast"), a DLNA renderer such as a TV or a receiver (upnp_renderer,
 * "stream_out_dlna").
 */
enum class RendererKind {
    Chromecast, Dlna, Other;

    companion object {
        fun of(type: String?): RendererKind = when (type?.trim()?.lowercase(Locale.ROOT)) {
            "chromecast" -> Chromecast
            "stream_out_dlna", "dlna", "upnp" -> Dlna
            else -> Other
        }
    }
}

/** S-1302: what makes two renderers found one device: its name as shown, whatever its case and spaces, and its kind. */
data class RendererKey(val name: String, val kind: RendererKind) {
    companion object {
        fun of(name: String, type: String?): RendererKey =
            RendererKey(name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT), RendererKind.of(type))
    }
}

/**
 * S-1302: the renderers found, one per device. A Chromecast answers on IPv4 and IPv6 and a DLNA
 * renderer on each of its addresses: the first found stands for the device, and another of the
 * same name and kind takes its place when it goes. [add] and [remove] say whether [devices]
 * changed.
 */
class RendererRoster<T>(private val keyOf: (T) -> RendererKey) {
    private val found = LinkedHashMap<RendererKey, MutableList<T>>()

    /** One item per device, in the order the devices were found. */
    val devices: List<T> get() = found.values.map { it.first() }

    fun add(item: T): Boolean {
        val same = found.getOrPut(keyOf(item)) { mutableListOf() }
        if (item in same) return false
        same += item
        return same.size == 1
    }

    fun remove(item: T): Boolean {
        val key = keyOf(item)
        val same = found[key] ?: return false
        val shown = same.first() == item
        if (!same.remove(item)) return false
        if (same.isEmpty()) found.remove(key)
        return shown
    }

    fun clear() = found.clear()
}

/** S-1302: what a renderer gets of an item; libVLC sends it, the music engine never does. */
enum class CastSupport {
    /** As it is, or converted by libVLC to what the device takes (a Chromecast's formats). */
    Sent,

    /** DSD: libVLC decodes it to PCM first; the device never gets DSD. */
    AsPcm,

    /** A tracker module: only the music engine renders every kind (libopenmpt), on this device. */
    LocalOnly,
}

/** S-1302: what casting needs to know of an item: a tracker module (S-911), a DSD file. */
data class CastItem(val tracker: Boolean, val dsd: Boolean) {
    val support: CastSupport
        get() = when {
            tracker -> CastSupport.LocalOnly
            dsd -> CastSupport.AsPcm
            else -> CastSupport.Sent
        }
}

/** What the playback service does with the item playing when a renderer is chosen or let go. */
sealed interface Handover {
    /** Nothing to hand over: libVLC goes on, sent to the renderer or back to this device. */
    data object None : Handover

    /** A tracker module goes on here; the items after it go to the renderer. */
    data object StaysHere : Handover

    /** The item at [index] plays again in [to] from [positionMs], paused if it was. */
    data class Replay(val to: EngineId, val index: Int, val positionMs: Long, val paused: Boolean) : Handover
}

/** Where the playback service stands, as a handover sees it; the playlist manager in the app, a fake in the tests. */
interface HandoverHost {
    /** The engine playing the current item. */
    val engine: EngineId

    /** The current item's place in the queue, -1 when there is none. */
    val index: Int

    val positionMs: Long

    val playing: Boolean

    val paused: Boolean

    /** The current item as casting sees it; null when there is none. */
    val item: CastItem?

    /** Whether the music engine takes the current item now (no renderer). */
    fun engineTakes(): Boolean

    /** Plays the item at [replay]'s index again in its engine, where it was; the queue stays as it is. */
    fun replay(replay: Handover.Replay)
}

/**
 * S-1302: the music engine plays on this device only. When a renderer is chosen while it plays,
 * its track goes on in libVLC, which sends it, where it was and in the same queue; when the
 * renderer is let go, a track the engine takes comes back to it. A tracker module stays with the
 * engine. Video, streams and everything else are libVLC's already: the renderer changes under them.
 */
object CastHandover {

    fun plan(host: HandoverHost, wasCasting: Boolean, casting: Boolean): Handover {
        if (wasCasting == casting || host.index < 0 || !(host.playing || host.paused)) return Handover.None
        val item = host.item ?: return Handover.None
        return if (casting) {
            when {
                host.engine != EngineId.NagmedarAudio -> Handover.None
                item.support == CastSupport.LocalOnly -> Handover.StaysHere
                else -> Handover.Replay(EngineId.LibVlc, host.index, host.positionMs.coerceAtLeast(0L), paused = !host.playing)
            }
        } else {
            if (host.engine == EngineId.LibVlc && host.engineTakes()) {
                Handover.Replay(EngineId.NagmedarAudio, host.index, host.positionMs.coerceAtLeast(0L), paused = !host.playing)
            } else {
                Handover.None
            }
        }
    }

    /** Plans the handover and, when there is one, has [host] replay the item. */
    fun change(host: HandoverHost, wasCasting: Boolean, casting: Boolean): Handover =
        plan(host, wasCasting, casting).also { if (it is Handover.Replay) host.replay(it) }

    /** Whether an item is passed over while casting: a tracker module, which plays here alone. */
    fun skippedWhileCasting(item: CastItem): Boolean = item.support == CastSupport.LocalOnly
}
