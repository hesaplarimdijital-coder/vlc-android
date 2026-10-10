package app.nagmedar.playback

/**
 * Playback engines behind the one PlaybackService and media session (ADR-05):
 * libVLC for video, streams, radio and podcasts, and the Nağmedar audio engine for the local
 * music library (ADR-06, MVP2).
 */
enum class EngineId { LibVlc, NagmedarAudio }

/** What an engine needs to know to decide whether it can play an item. */
data class EngineMedia(
    val uri: String,
    val isAudio: Boolean,
    /** file:// or content:// on this device, as opposed to network streams. */
    val isLocal: Boolean,
    val mimeType: String? = null,
)

/**
 * Transport contract shared by both engines. It is the subset of the classic PlayerController
 * that PlaylistManager uses to drive playback, so the libVLC player implements it as is.
 */
interface PlaybackEngine {
    val engineId: EngineId

    /** Whether this engine plays [media]. */
    fun canPlay(media: EngineMedia): Boolean

    fun play()

    /** Pauses; false when the media cannot be paused. */
    fun pause(): Boolean

    fun stop()

    fun isPlaying(): Boolean

    fun isPaused(): Boolean

    fun setTime(time: Long, fast: Boolean = false)

    fun getCurrentTime(): Long

    fun getLength(): Long

    fun getRate(): Float

    fun setRate(rate: Float, save: Boolean)

    fun getVolume(): Int

    fun setVolume(volume: Int): Int

    fun release()
}

/** Chooses the engine for an item: the first engine that accepts it, libVLC otherwise. */
class EngineRouter(private val engines: List<PlaybackEngine>) {

    init {
        require(engines.isNotEmpty()) { "at least one engine" }
    }

    val default: PlaybackEngine get() = engines.first()

    fun engineFor(media: EngineMedia): PlaybackEngine = engines.firstOrNull { it.canPlay(media) } ?: default
}

/**
 * Keeps the current engine for the playback service. Switching engines between items stops the
 * previous one and carries volume and rate over; the service, its media session and the
 * notification stay the same object, so controllers (lock screen, Bluetooth, Android Auto)
 * never see the switch.
 */
class EngineHost(private val router: EngineRouter, private val onEngineChanged: (from: EngineId, to: EngineId) -> Unit = { _, _ -> }) {

    var current: PlaybackEngine = router.default
        private set

    fun prepareFor(media: EngineMedia): PlaybackEngine {
        val next = router.engineFor(media)
        if (next !== current) {
            val previous = current
            val volume = previous.getVolume()
            val rate = previous.getRate()
            previous.stop()
            next.setVolume(volume)
            next.setRate(rate, false)
            current = next
            onEngineChanged(previous.engineId, next.engineId)
        }
        return current
    }
}
