package app.nagmedar.ui.podcast

/**
 * S-205: the wait of an episode's play button while the player opens the episode ([PodcastController]): a pause button
 * that is not pressable until the player plays it at the place it was opened at for [SETTLED_LOOKS] looks running (the
 * stream's opening, the start, a seek do not flip it back), until it took [START_LOOKS] looks, until the page went, or
 * at once when the player ended, failed on or was stopped on the episode (its halt stamp moved:
 * [EpisodeControl.haltStamp]). Pure: the controller looks and keeps [episodeId] as the page's state.
 */
internal class StartWait {
    /** The episode being opened; null when nothing waits. */
    var episodeId: Long? = null
        private set
    private var targetMs = 0L
    private var looks = 0
    private var settled = 0
    private var haltStamp = 0

    /** [episodeId] is being opened at [targetMs]; [haltStamp] is its halt stamp now. */
    fun start(episodeId: Long, targetMs: Long, haltStamp: Int) {
        this.episodeId = episodeId
        this.targetMs = targetMs
        this.haltStamp = haltStamp
        looks = 0
        settled = 0
    }

    /** One look: [rowId] is the episode whose page is open, [now] where the player has it, [haltStamp] its halt stamp. */
    fun look(rowId: Long?, now: Listening?, haltStamp: Int): Long? {
        val id = episodeId ?: return null
        val there = now != null && now.playing && now.lengthMs > 0 && now.positionMs + START_SLACK_MS >= targetMs
        settled = if (there) settled + 1 else 0
        if (rowId != id || haltStamp > this.haltStamp || settled >= SETTLED_LOOKS || ++looks >= START_LOOKS) episodeId = null
        return episodeId
    }

    fun clear() {
        episodeId = null
    }

    companion object {
        /** How far before the place it was opened at an episode may start (the start is set in whole seconds). */
        const val START_SLACK_MS = 2_000L

        /** Looks of it playing there before the button follows the player again. */
        const val SETTLED_LOOKS = 2

        /** The most looks the button waits for the player: 20 s, a slow stream's opening. */
        const val START_LOOKS = 50
    }
}
