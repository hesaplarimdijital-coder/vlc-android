package app.nagmedar.ui.podcast

/** S-1104: what the show of the playing episode asks of its playback. */
data class EpisodeContext(
    val episodeId: Long,
    val feedId: Long,
    val speed: Float,
    val introSeconds: Int,
    val outroSeconds: Int,
    val positionMs: Long,
    val played: Boolean,
    val durationMs: Long,
    /** The show's volume boost ([VolumeBoost]): the observer applies it, the tracker does not look at it. */
    val volumeBoost: Int = VolumeBoost.OFF,
)

/** S-1104: what [EpisodeTracker] asks the playback service and the database to do. */
sealed interface TrackerCommand {
    data class SetRate(val rate: Float) : TrackerCommand

    data class SeekTo(val ms: Long) : TrackerCommand

    /** The place reached; [played] marks the episode played too. */
    data class SavePosition(val episodeId: Long, val positionMs: Long, val played: Boolean) : TrackerCommand

    /** The speed the listener chose: the show's from now on. */
    data class SaveSpeed(val feedId: Long, val speed: Float) : TrackerCommand

    /** The outro began: the next episode, or stop when there is none. */
    data object Finish : TrackerCommand
}

/**
 * S-1104: follows the media the playback service plays, a look at a time, and says what a podcast episode
 * needs: its show's speed once it has started, the intro skipped (or the place it was left found) when it was
 * not started at the right place, the place saved every few seconds and whenever it pauses, stops or changes,
 * the episode marked played in its last 5 % or its outro, and the next one started when the outro begins. A
 * speed the listener sets while it plays becomes the show's. It holds no clock and no service: the caller gives
 * the facts of each look and does what comes back, so every rule is tested.
 */
class EpisodeTracker {
    private var location: String? = null
    private var context: EpisodeContext? = null
    private var startedAt = 0L
    private var applied = false
    private var desiredRate = 1f
    private var lastRate = 1f
    private var reapplied = 0
    private var lastSavedAt = 0L
    private var lastTime = 0L
    private var lastLength = 0L
    private var wasPlaying = false
    private var markedPlayed = false
    private var finished = false

    /**
     * One look. [location] is what the service plays (null for nothing); [context] the episode that is, once
     * known (null for a media that is no episode, or not yet asked); [timeMs], [lengthMs], [playing] and [rate]
     * its state; [nowMs] the clock. The commands come back in the order they are done.
     */
    fun sample(location: String?, context: EpisodeContext?, timeMs: Long, lengthMs: Long, playing: Boolean, rate: Float, nowMs: Long): List<TrackerCommand> {
        val out = ArrayList<TrackerCommand>(2)
        if (location != this.location) {
            leave(out)
            this.location = location
            this.context = null
            startedAt = nowMs
            applied = false
            reapplied = 0
            lastSavedAt = nowMs
            lastTime = 0
            lastLength = 0
            wasPlaying = false
            markedPlayed = false
            finished = false
        }
        if (this.context == null) this.context = context
        val episode = this.context ?: return out
        if (lengthMs <= 0 || location == null) {
            wasPlaying = playing
            return out
        }
        if (!applied) {
            applied = true
            startedAt = nowMs
            lastSavedAt = nowMs
            desiredRate = PlaybackRules.clampSpeed(episode.speed)
            lastRate = desiredRate
            if (!PlaybackRules.sameSpeed(rate, desiredRate)) out += TrackerCommand.SetRate(desiredRate)
            // started from the beginning by something that knew nothing of the show: the intro skipped, the place found
            val start = PlaybackRules.startMs(episode.positionMs, episode.played, lengthMs, episode.introSeconds, episode.outroSeconds)
            if (timeMs < PlaybackRules.RESUME_MIN_MS && start - timeMs > PlaybackRules.RESUME_MIN_MS) out += TrackerCommand.SeekTo(start)
            wasPlaying = playing
            lastTime = timeMs
            lastLength = lengthMs
            return out
        }
        // the player went back to 0 after the end of the media: that is not a place to keep
        if (timeMs == 0L && !playing && lastTime > PlaybackRules.RESUME_MIN_MS) {
            wasPlaying = false
            return out
        }
        lastTime = timeMs
        lastLength = lengthMs
        followRate(episode, rate, nowMs, out)
        if (playing && !finished) {
            val outro = PlaybackRules.outroStartMs(lengthMs, episode.outroSeconds)
            if (outro != null && timeMs >= outro) {
                finished = true
                markedPlayed = true
                out += TrackerCommand.SavePosition(episode.episodeId, timeMs, played = true)
                out += TrackerCommand.Finish
                wasPlaying = playing
                return out
            }
        }
        val played = !markedPlayed && PlaybackRules.isPlayed(timeMs, lengthMs, episode.outroSeconds)
        if (played) markedPlayed = true
        val due = playing && nowMs - lastSavedAt >= SAVE_EVERY_MS
        val paused = wasPlaying && !playing
        if (played || due || paused) {
            out += TrackerCommand.SavePosition(episode.episodeId, timeMs, played)
            lastSavedAt = nowMs
        }
        wasPlaying = playing
        return out
    }

    /** The service is going: the place of the episode that was playing. */
    fun end(): List<TrackerCommand> {
        val out = ArrayList<TrackerCommand>(1)
        leave(out)
        location = null
        context = null
        return out
    }

    private fun followRate(episode: EpisodeContext, rate: Float, nowMs: Long, out: MutableList<TrackerCommand>) {
        if (PlaybackRules.sameSpeed(rate, lastRate)) return
        // the player resets the speed to normal as a media starts: the show's speed goes back, a few times at most
        if (nowMs - startedAt < GRACE_MS && PlaybackRules.sameSpeed(rate, 1f) && reapplied < MAX_REAPPLY) {
            reapplied++
            out += TrackerCommand.SetRate(desiredRate)
            return
        }
        val chosen = PlaybackRules.clampSpeed(rate)
        desiredRate = chosen
        lastRate = rate
        out += TrackerCommand.SaveSpeed(episode.feedId, chosen)
    }

    /** The episode that was playing is left: where it got to, played when it went far enough. */
    private fun leave(out: MutableList<TrackerCommand>) {
        val episode = context ?: return
        if (!applied || lastLength <= 0) return
        val played = !markedPlayed && PlaybackRules.isPlayed(lastTime, lastLength, episode.outroSeconds)
        out += TrackerCommand.SavePosition(episode.episodeId, lastTime, played)
    }

    companion object {
        /** The place is saved this often while an episode plays. */
        const val SAVE_EVERY_MS = 5_000L

        /** For this long after an episode starts, a speed reset to normal is the player's, not the listener's. */
        const val GRACE_MS = 8_000L
        private const val MAX_REAPPLY = 3
    }
}
