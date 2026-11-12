package app.nagmedar.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.nagmedar.ui.podcast.EpisodeAddresses
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.0.4 (the owner, 12 Nov: "podcast çaldığında canlı yayın diyor müzik barı. Canlı yayın olmamalı"): what a timeline
 * makes of the length of what plays. 1.0.2 took every stream without a length of its own for a station on the air and
 * drew "● Canlı yayın" in the seek bar's place; a podcast episode streamed from its enclosure (not downloaded) is such
 * a stream too, until the player has found its length, and for the bars it stayed one. An episode is never on the air:
 * it has a seek bar, the player is asked for its length as it is for a file the library has none for, and until the
 * player answers the length its feed gives stands in.
 */
internal object PlayingLength {
    /**
     * On the air: a stream (no file of this device, [isStream]) that has no length of its [own] and is no podcast
     * [episode]. The bars then show that it is live where the seek bar would be.
     */
    fun live(own: Long, scheme: String?, episode: Boolean): Boolean = own <= 0L && isStream(scheme) && !episode

    /**
     * Whether the player is asked for the length: a file the library has none for, and an episode's stream. A station
     * on the air has none to ask for.
     */
    fun asksPlayer(own: Long, scheme: String?, episode: Boolean): Boolean = own <= 0L && (!isStream(scheme) || episode)

    /**
     * The length a timeline shows: the media's [own], else the [player]'s, else the episode's as its [feed] gives it
     * (null: no episode); 0 while none of them knows it.
     */
    fun shown(own: Long, player: Long, feed: Long?): Long = when {
        own > 0L -> own
        player > 0L -> player
        else -> feed?.coerceAtLeast(0L) ?: 0L
    }
}

/**
 * The podcast episode [media] is the audio of, as far as a timeline asks: the length its feed gives it (0 when the feed
 * gives none); null for anything else. An episode started in this process is known at once, before it plays
 * ([EpisodeAddresses]). A stream nobody here started (the list the player was left with, restored at the next start)
 * is asked of Room once: null until the answer comes, and so on the air for that moment, as a station is for good.
 */
@Composable
internal fun rememberEpisodeLength(media: MediaWrapper?): Long? {
    val location = media?.location
    val started = EpisodeAddresses.lengthOf(location)
    val asked = remember(location) { mutableStateOf<Long?>(null) }
    val app = LocalContext.current.applicationContext
    // only a stream without a length is in doubt: a file is never taken for a station
    val inDoubt = media != null && started == null && media.length <= 0L && isStream(media.uri?.scheme)
    LaunchedEffect(location, inDoubt) {
        if (location != null && inDoubt) asked.value = EpisodeAddresses.ask(app, location)
    }
    return started ?: asked.value
}
