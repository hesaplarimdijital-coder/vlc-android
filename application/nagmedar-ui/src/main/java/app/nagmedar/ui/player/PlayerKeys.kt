package app.nagmedar.ui.player

import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import app.nagmedar.ui.R
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.player.navigate.chapterStep
import app.nagmedar.ui.podcast.ChapterStep
import app.nagmedar.ui.podcast.ChapterSteps
import app.nagmedar.ui.podcast.PodcastCenter
import org.videolan.libvlc.MediaPlayer
import org.videolan.vlc.gui.video.PlayerWindow
import org.videolan.vlc.gui.video.VideoPlayerActivity

/** What the video player does for a shortcut (S-602); false when it has nothing to do for it. */
fun interface PlayerKeyTarget {
    fun perform(id: ShortcutId): Boolean
}

/**
 * The video player's keys (S-602): the shortcut table's, so VLC's by default (Space, F, M, G/H,
 * J/K, E, A, C, Z, T, V, B, Ctrl+T, Shift+S…) and the user's when changed (S-402), on any keyboard
 * layout (S-410). A key the table gave away is no longer the classic player's either; a key that
 * is in no table (the arrows alone, the media keys) stays the classic player's.
 */
object PlayerKeys {

    /** Held down, these go on (seeking, the volume, the delays, the speed, the frames); the rest act once. */
    private val repeating = setOf(
        ShortcutId.JumpBackShort, ShortcutId.JumpForwardShort, ShortcutId.JumpBack, ShortcutId.JumpForward,
        ShortcutId.JumpBackLong, ShortcutId.JumpForwardLong, ShortcutId.NextFrame, ShortcutId.PreviousFrame,
        ShortcutId.Slower, ShortcutId.Faster, ShortcutId.VolumeUp, ShortcutId.VolumeDown,
        ShortcutId.AudioDelayDown, ShortcutId.AudioDelayUp, ShortcutId.SubtitleDelayDown, ShortcutId.SubtitleDelayUp,
    )

    private val scopes = setOf(ShortcutScope.Global, ShortcutScope.Player)
    private val playerScope = setOf(ShortcutScope.Player)

    /**
     * Whether the key [event] pressed is the table's in the player; [target] does what it says:
     * the player's own keys, and the app's (the guide, the shortcut card, the palette…). The
     * shipped tables and the presets keep the two apart (no conflicts); a chord the user gave
     * both, which the shortcut editor shows as a conflict, stays the player's here.
     */
    fun handle(event: KeyEvent, target: PlayerKeyTarget): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || ShortcutRegistry.recording) return false
        val key = ComposeKeyEvent(event)
        val shortcut = ShortcutRegistry.match(key, playerScope) ?: ShortcutRegistry.match(key, scopes)
        if (shortcut == null) {
            // VLC's key, given away by the user: not the classic player's any more
            return ShortcutRegistry.defaults.any { it.scope in scopes && it.chord?.matches(key) == true }
        }
        if (event.repeatCount > 0 && shortcut.id !in repeating) return true
        target.perform(shortcut.id)
        // a shortcut of the table, done or not here (the palette, the sidebar): not the classic player's
        return true
    }
}

/**
 * The classic video player doing the table's shortcuts (S-602); what they change is said by the
 * new interface's OSD ([say]), never by the classic overlay.
 */
internal class ActivityKeyTarget(private val activity: VideoPlayerActivity) : PlayerKeyTarget {

    override fun perform(id: ShortcutId): Boolean {
        val service = activity.service ?: return false
        when (id) {
            ShortcutId.PlayPause -> activity.doPlayPause()
            ShortcutId.Stop -> activity.stop()
            ShortcutId.Previous -> activity.previous()
            ShortcutId.Next -> activity.next()
            ShortcutId.JumpBackShort -> activity.seekBy(-3_000)
            ShortcutId.JumpForwardShort -> activity.seekBy(3_000)
            ShortcutId.JumpBack -> activity.seekBy(-10_000)
            ShortcutId.JumpForward -> activity.seekBy(10_000)
            ShortcutId.JumpBackLong -> activity.seekBy(-60_000)
            ShortcutId.JumpForwardLong -> activity.seekBy(60_000)
            // S-504: libVLC's own next frame when this build has it, else a frame's worth of seeking
            ShortcutId.NextFrame -> if (!LibVlcExtensions.nextFrame(service.mediaplayer)) activity.stepFrame(forward = true)
            ShortcutId.PreviousFrame -> activity.stepFrame(forward = false)
            ShortcutId.Slower -> activity.decreaseRate()
            ShortcutId.Faster -> activity.increaseRate()
            ShortcutId.NormalSpeed -> activity.resetRate()
            ShortcutId.Repeat -> {
                service.repeatType = when (service.repeatType) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                    PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
                say(
                    activity.getString(
                        when (service.repeatType) {
                            PlaybackStateCompat.REPEAT_MODE_ALL -> R.string.nag_player_repeat_all
                            PlaybackStateCompat.REPEAT_MODE_ONE -> R.string.nag_player_repeat_one
                            else -> R.string.nag_player_repeat_none
                        },
                    ),
                )
            }
            ShortcutId.Shuffle -> service.shuffle()
            ShortcutId.ShowTime -> activity.chrome?.show()
            ShortcutId.JumpToTime -> activity.showJumpToTime()
            ShortcutId.NextChapter -> stepChapter(forward = true)
            ShortcutId.PreviousChapter -> stepChapter(forward = false)
            // S-1104: the chapters of a podcast episode, where it plays as a video
            ShortcutId.PodcastNextChapter -> stepEpisodeChapter(forward = true)
            ShortcutId.PodcastPreviousChapter -> stepEpisodeChapter(forward = false)
            ShortcutId.VolumeUp -> activity.volumeStep(up = true)
            ShortcutId.VolumeDown -> activity.volumeStep(up = false)
            ShortcutId.Mute -> activity.toggleMute()
            ShortcutId.AudioDelayDown -> activity.delayAudio(-DELAY_STEP_US)
            ShortcutId.AudioDelayUp -> activity.delayAudio(DELAY_STEP_US)
            ShortcutId.NextAudioTrack -> nextTrack(audio = true)
            ShortcutId.Fullscreen -> PlayerWindow.toggleFullscreen(activity)
            // the controls take the screenshot themselves (S-606); casting has no picture to take
            ShortcutId.Snapshot -> return false
            ShortcutId.Equalizer -> activity.showEqualizer()
            ShortcutId.AspectRatio -> nextScale(aspectRatios)
            ShortcutId.Crop -> nextScale(crops)
            ShortcutId.Zoom -> nextScale(zooms)
            ShortcutId.NextSubtitle -> nextTrack(audio = false)
            ShortcutId.SubtitleDelayDown -> activity.delaySubtitles(-DELAY_STEP_US)
            ShortcutId.SubtitleDelayUp -> activity.delaySubtitles(DELAY_STEP_US)
            // the controls open the system's picker themselves (S-701)
            ShortcutId.AddSubtitleFile -> return false
            else -> return false
        }
        return true
    }

    /** V and B, as on VLC's desktop: the next track, the subtitles' "off" among them; its name shows. */
    private fun nextTrack(audio: Boolean) {
        val service = activity.service ?: return
        val tracks = (if (audio) service.audioTracks else service.spuTracks).orEmpty()
        if (tracks.isEmpty()) return
        val current = if (audio) service.audioTrack else service.spuTrack
        val next = tracks[(tracks.indexOfFirst { it.getId() == current } + 1).mod(tracks.size)]
        if (audio) service.setAudioTrack(next.getId()) else service.setSpuTrack(next.getId())
        val name = if (next.getId() == NO_TRACK) activity.getString(R.string.nag_player_subtitles_off) else next.getName()
        say(activity.getString(if (audio) R.string.nag_player_audio_track_now else R.string.nag_player_subtitles_now, name))
    }

    /**
     * Shift+N and Shift+P (S-608): the next chapter, or back to the start of this one, then the one
     * before; its number and name show.
     */
    private fun stepChapter(forward: Boolean) {
        val service = activity.service ?: return
        val chapters = service.getChapters(-1)?.map { ChapterMark(it.timeOffset, it.name.orEmpty()) }.orEmpty()
        // a podcast episode's chapters, when the file gives none
        if (chapters.isEmpty() && PodcastCenter.playing.value?.chapters.orEmpty().isNotEmpty()) return stepEpisodeChapter(forward)
        val index = chapterStep(chapters, service.getTime(), forward) ?: return
        service.chapterIdx = index
        val name = chapters[index].name
        say(if (name.isBlank()) activity.getString(R.string.nag_player_chapter, index + 1) else activity.getString(R.string.nag_player_chapter_named, index + 1, name))
    }

    /** Ctrl+Alt+→ and ←, as everywhere (S-1104): the next chapter of the podcast episode, or back to the start of this one; its name shows. */
    private fun stepEpisodeChapter(forward: Boolean) {
        when (val step = ChapterSteps.step(forward)) {
            ChapterStep.NoChapters -> say(activity.getString(R.string.nag_pod_chapter_none))
            ChapterStep.AtEnd -> say(activity.getString(R.string.nag_pod_chapter_last))
            is ChapterStep.Went -> {
                val name = step.chapter.title
                say(if (name.isBlank()) activity.getString(R.string.nag_player_chapter, step.index + 1) else activity.getString(R.string.nag_player_chapter_named, step.index + 1, name))
            }
        }
    }

    /** A, C and Z go round their own sizes of the picture, back to fitting it; its name shows. */
    private fun nextScale(cycle: List<MediaPlayer.ScaleType>) {
        val index = cycle.indexOf(activity.currentScaleType)
        applyPictureSize(activity, cycle[(index + 1).mod(cycle.size)])
    }

    /** [text] said over the video by the new interface's OSD. */
    private fun say(text: String) {
        activity.chrome?.showInfo(text, SAID_MS)
    }

    private companion object {
        /** VLC's step of the delays: 50 ms, in µs. */
        const val DELAY_STEP_US = 50_000L

        const val SAID_MS = 1_000

        const val NO_TRACK = "-1"

        val aspectRatios = listOf(
            MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_16_9, MediaPlayer.ScaleType.SURFACE_4_3,
            MediaPlayer.ScaleType.SURFACE_16_10, MediaPlayer.ScaleType.SURFACE_2_1, MediaPlayer.ScaleType.SURFACE_221_1,
            MediaPlayer.ScaleType.SURFACE_235_1, MediaPlayer.ScaleType.SURFACE_239_1, MediaPlayer.ScaleType.SURFACE_5_4,
        )

        /**
         * Until S-505's crop, the picture cropped to fill the screen (libVLC's "fit screen"; its
         * "fill" stretches the picture instead), and back.
         */
        val crops = listOf(MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)

        /** Fitted, cropped to fill, then pixel for pixel. */
        val zooms = listOf(MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_FIT_SCREEN, MediaPlayer.ScaleType.SURFACE_ORIGINAL)
    }
}
