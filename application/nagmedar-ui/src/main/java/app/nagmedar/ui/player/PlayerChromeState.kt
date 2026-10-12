package app.nagmedar.ui.player

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import java.text.NumberFormat
import java.util.Locale

/** A chapter of the media: where it starts, and its name (empty when the file gives none). */
@Immutable
data class ChapterMark(val startMs: Long, val name: String)

/** A track the menus offer: its id for the player and its name. */
@Immutable
data class TrackChoice(val id: String, val name: String)

/**
 * What the desktop controls over the video show (S-601): the player's state, read twice a second
 * from the playback service in the app, written by hand in the tests.
 */
@Immutable
data class PlayerChromeState(
    val title: String = "",
    val playing: Boolean = false,
    val timeMs: Long = 0L,
    val lengthMs: Long = 0L,
    val chapters: List<ChapterMark> = emptyList(),
    /** The A-B repeat's points, -1 when not set. */
    val abStartMs: Long = -1L,
    val abStopMs: Long = -1L,
    val rate: Float = 1f,
    val audioTracks: List<TrackChoice> = emptyList(),
    val audioTrack: String? = null,
    val subtitleTracks: List<TrackChoice> = emptyList(),
    /** The subtitle shown, null when none is. */
    val subtitleTrack: String? = null,
    /** The volume in percent, above 100 with audio boost, up to [maxVolume]. */
    val volume: Int = 100,
    val maxVolume: Int = 100,
    val muted: Boolean = false,
    val fullscreen: Boolean = false,
    /** Whether the window can fill the screen and come back (a desktop window, Android 14 and later). */
    val canFullscreen: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** S-603: the panel shows its effects. */
    val effectsOpen: Boolean = false,
    /** S-606: the screenshots' choices, for the screenshot button's menu. */
    val snapshot: SnapshotSettings = SnapshotSettings(),
    /** S-608: the film's bookmarks, marked on the timeline. */
    val bookmarks: List<Long> = emptyList(),
    /** S-609: the device has picture in picture, or VLC's own small window. */
    val canPictureInPicture: Boolean = false,
) {
    /** The index of the chapter playing at [ms], or null. */
    fun chapterAt(ms: Long): Int? = chapters.indexOfLast { it.startMs <= ms }.takeIf { it >= 0 }

    val abState: AbState
        get() = when {
            abStartMs < 0 -> AbState.Off
            abStopMs < 0 -> AbState.HasA
            else -> AbState.Looping
        }
}

/** The A-B repeat's button: sets A, then B, then turns the loop off. */
enum class AbState { Off, HasA, Looping }

/** What the desktop controls do (S-601): the player's in the app, a fake in the tests. */
interface PlayerChromeActions {
    fun togglePlayPause()

    fun previous()

    fun next()

    fun seekTo(ms: Long)

    fun seekBy(deltaMs: Int)

    fun setRate(rate: Float)

    fun setAudioTrack(id: String)

    /** [SUBTITLES_OFF] turns them off. */
    fun setSubtitleTrack(id: String)

    /** A at the time playing, then B, then off. */
    fun abRepeat()

    fun setVolume(percent: Int)

    fun toggleMute()

    fun toggleFullscreen()

    /** S-603: opens the panel on its effects, or closes it. */
    fun toggleEffects()

    /** S-606: a screenshot of the film, as the settings ask for it. */
    fun takeSnapshot()

    /** S-606: the screenshots' choices, from the button's menu. */
    fun setSnapshotSettings(settings: SnapshotSettings)

    /** S-606: the system's folder picker, for the screenshots' folder. */
    fun chooseSnapshotFolder()

    /** S-609: the film goes on in a small window over the others. */
    fun pictureInPicture()

    /** S-701: the system's file picker, for subtitle files to add to the film. */
    fun addSubtitleFile()

    /** S-702: the character set the film's subtitle files are read in, chosen for the film. */
    fun chooseSubtitleEncoding()

    /** Leaves the player. */
    fun close()

    companion object {
        /** The player's id of "no track". */
        const val SUBTITLES_OFF = "-1"
    }
}

/** The speeds of the speed menu, as VLC desktop offers them. */
val playbackRates: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

/** "1,0×", "0,25×" in Turkish; "1.0×" in English. */
fun rateLabel(rate: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }.format(rate) + "×"

/** "1:11:45", "4:05", "0:00": a time of the player. */
fun playerClock(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest) else "%d:%02d".format(Locale.ROOT, minutes, rest)
}
