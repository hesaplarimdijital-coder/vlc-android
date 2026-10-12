package app.nagmedar.ui.player

import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import app.nagmedar.ui.R
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
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

    /** Whether the key [event] pressed is the table's in the player; [target] does what it says. */
    fun handle(event: KeyEvent, target: PlayerKeyTarget): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || ShortcutRegistry.recording) return false
        val key = ComposeKeyEvent(event)
        val shortcut = ShortcutRegistry.match(key, scopes)
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

/** The classic video player doing the table's shortcuts (S-602). */
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
            ShortcutId.NextFrame -> activity.stepFrame(forward = true)
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
                activity.overlayDelegate.showInfo(
                    when (service.repeatType) {
                        PlaybackStateCompat.REPEAT_MODE_ALL -> R.string.nag_player_repeat_all
                        PlaybackStateCompat.REPEAT_MODE_ONE -> R.string.nag_player_repeat_one
                        else -> R.string.nag_player_repeat_none
                    },
                    1000,
                )
            }
            ShortcutId.Shuffle -> service.shuffle()
            ShortcutId.ShowTime -> activity.overlayDelegate.showOverlay()
            ShortcutId.JumpToTime -> activity.showJumpToTime()
            ShortcutId.VolumeUp -> activity.volumeStep(up = true)
            ShortcutId.VolumeDown -> activity.volumeStep(up = false)
            ShortcutId.Mute -> activity.toggleMute()
            ShortcutId.AudioDelayDown -> activity.delayAudio(-DELAY_STEP_US)
            ShortcutId.AudioDelayUp -> activity.delayAudio(DELAY_STEP_US)
            ShortcutId.NextAudioTrack -> nextTrack(audio = true)
            ShortcutId.Fullscreen -> PlayerWindow.toggleFullscreen(activity)
            ShortcutId.Snapshot -> activity.takeScreenshot()
            ShortcutId.Equalizer -> activity.showEqualizer()
            ShortcutId.AspectRatio -> nextScale(aspectRatios)
            ShortcutId.Crop -> nextScale(crops)
            ShortcutId.Zoom -> nextScale(zooms)
            ShortcutId.NextSubtitle -> nextTrack(audio = false)
            ShortcutId.SubtitleDelayDown -> activity.delaySubtitles(-DELAY_STEP_US)
            ShortcutId.SubtitleDelayUp -> activity.delaySubtitles(DELAY_STEP_US)
            ShortcutId.AddSubtitleFile -> activity.overlayDelegate.pickSubtitles()
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
        activity.overlayDelegate.showInfo(
            activity.getString(if (audio) R.string.nag_player_audio_track_now else R.string.nag_player_subtitles_now, name),
            1000,
        )
    }

    /** A, C and Z go round their own sizes of the picture, back to fitting it. */
    private fun nextScale(cycle: List<MediaPlayer.ScaleType>) {
        val index = cycle.indexOf(activity.currentScaleType)
        activity.resizeDelegate.setVideoScale(cycle[(index + 1).mod(cycle.size)])
    }

    private companion object {
        /** VLC's step of the delays: 50 ms, in µs. */
        const val DELAY_STEP_US = 50_000L

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
