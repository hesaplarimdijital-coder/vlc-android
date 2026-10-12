package app.nagmedar.ui.stats

/**
 * S-808: a track's listening, counted as it plays. A play counts once half of the track, or four
 * minutes of it, is heard (as Last.fm counts); a skip when another track takes over before that,
 * once a second of it was heard. A stop is neither. [sample] is fed about once a second with what
 * plays and where; the time heard grows only by the steps a playback makes, so a seek is not
 * listening. A track that starts again from its beginning once counted (repeat one, a seek back to
 * the start) counts again.
 */
class ListenCounter(private val onPlayed: (key: String) -> Unit, private val onSkipped: (key: String) -> Unit) {

    private var key: String? = null
    private var length = 0L
    private var heard = 0L
    private var last = 0L
    private var counted = false

    /** What plays ([key]: null for nothing, or nothing counted), where ([timeMs] of [lengthMs]), and whether it plays. */
    fun sample(key: String?, timeMs: Long, lengthMs: Long, playing: Boolean) {
        if (key != this.key) {
            val previous = this.key
            if (previous != null && !counted && key != null && heard >= MIN_SKIP_MS) onSkipped(previous)
            this.key = key
            length = lengthMs
            heard = 0
            last = timeMs
            counted = false
            return
        }
        if (key == null) return
        if (lengthMs > 0) length = lengthMs
        val step = timeMs - last
        last = timeMs
        if (counted && timeMs < RESTART_MS && step < -RESTART_MS) {
            // from its beginning again
            heard = 0
            counted = false
        }
        if (playing && step in 1..MAX_STEP_MS) heard += step
        if (!counted && heard >= threshold()) {
            counted = true
            onPlayed(key)
        }
    }

    private fun threshold(): Long = if (length > 0) minOf(length / 2, MAX_THRESHOLD_MS) else MAX_THRESHOLD_MS

    companion object {
        /** Four minutes heard make a play whatever the track's length. */
        const val MAX_THRESHOLD_MS = 4 * 60_000L

        /** Less heard than this is not a skip: the track was only passed through. */
        const val MIN_SKIP_MS = 1_000L

        /** The longest step a playback makes between two samples (a second at up to four times the speed); longer is a seek. */
        const val MAX_STEP_MS = 4_500L

        /** Back within this of its beginning, from further on: the track starts again. */
        const val RESTART_MS = 3_000L
    }
}
