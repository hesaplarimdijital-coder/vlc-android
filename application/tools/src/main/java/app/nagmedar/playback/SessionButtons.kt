package app.nagmedar.playback

/** A custom action the media session offers beside play, pause and skip. */
enum class SessionButton { Shuffle, Repeat, Rewind, Forward, Speed, EpisodeBack, EpisodeForward }

/**
 * 1.0: the media session's custom actions, the one rule of Android Auto, the system's media controls (notification and
 * lock screen) and Wear: while a podcast episode plays, [SessionButton.EpisodeBack] and [SessionButton.EpisodeForward]
 * ([EPISODE_BACK_S] back, [EPISODE_FORWARD_S] forward) instead of the music's, and in the car the speed too (0.5's
 * podcast mode had it there); music keeps its own, shuffle and repeat in the car (with the seek and speed buttons when
 * Settings asks for them), the seek buttons on the system's controls.
 */
object SessionButtons {
    /** How far an episode's back button goes, in seconds. */
    const val EPISODE_BACK_S = 15

    /** How far an episode's forward button goes, in seconds. */
    const val EPISODE_FORWARD_S = 30

    /**
     * The buttons, in order. [episode]: a podcast episode plays; [carMode]: Android Auto is connected;
     * [systemControls]: Android 13 or later, whose notification and lock screen draw the session's actions;
     * [seekButtons] and [speedButtons]: Settings › Android Auto's switches.
     */
    fun of(episode: Boolean, carMode: Boolean, systemControls: Boolean, seekButtons: Boolean = false, speedButtons: Boolean = false): List<SessionButton> = when {
        episode && carMode -> listOf(SessionButton.EpisodeBack, SessionButton.EpisodeForward, SessionButton.Speed)
        episode -> listOf(SessionButton.EpisodeBack, SessionButton.EpisodeForward)
        systemControls && !carMode -> listOf(SessionButton.Rewind, SessionButton.Forward)
        else -> buildList {
            add(SessionButton.Shuffle)
            add(SessionButton.Repeat)
            if (seekButtons) {
                add(SessionButton.Rewind)
                add(SessionButton.Forward)
            }
            if (speedButtons) add(SessionButton.Speed)
        }
    }

    /** Where [SessionButton.EpisodeBack] or [SessionButton.EpisodeForward] go from [timeMs] in a media of [lengthMs] (0: unknown). */
    fun episodeSeek(forward: Boolean, timeMs: Long, lengthMs: Long): Long = if (forward) {
        val to = timeMs + EPISODE_FORWARD_S * 1_000L
        if (lengthMs > 0) to.coerceAtMost(lengthMs) else to
    } else {
        (timeMs - EPISODE_BACK_S * 1_000L).coerceAtLeast(0L)
    }
}
