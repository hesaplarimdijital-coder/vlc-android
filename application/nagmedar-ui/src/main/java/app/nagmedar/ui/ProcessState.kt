package app.nagmedar.ui

import androidx.compose.runtime.snapshots.Snapshot
import app.nagmedar.ui.audio.AbTest
import app.nagmedar.ui.cast.Casting
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.lyrics.editor.LyricsEditorRequest
import app.nagmedar.ui.playback.ResumePrompts
import app.nagmedar.ui.player.effects.AdvancedVideoSession
import app.nagmedar.ui.player.record.Recordings
import app.nagmedar.ui.player.subtitles.tools.SubtitleToolsLauncher
import app.nagmedar.ui.player.subtitles.whisper.WhisperLauncher
import app.nagmedar.ui.transcode.TranscodeRequests

/**
 * The process-wide holders of Compose state: objects that live as long as the app and that any
 * window, service or worker may read. Each is made once, in the global snapshot, before any
 * window composes, so that no window's snapshot owns it.
 *
 * Why: Compose state made inside a composition belongs to that composition's snapshot until
 * the snapshot is applied, and another window or thread reading it meanwhile crashes
 * ("Reading a state that was created after the snapshot was taken"). Making it in the global
 * snapshot from inside a composition is no better: the composition's own snapshot, taken
 * earlier, cannot see it and crashes on its first write. So the objects are touched here, from
 * Application.onCreate on the main thread, where the global snapshot is the current one.
 */
object ProcessState {

    /** Makes every holder listed, in the global snapshot. Idempotent; main thread. Returns how many it touched. */
    fun warmUp(): Int = Snapshot.global {
        val holders: List<Any> = listOf(
            ShortcutRegistry,
            PhysicalKeys,
            Casting,
            LyricsEditorRequest,
            AbTest,
            ResumePrompts,
            TranscodeRequests,
            AdvancedVideoSession,
            WhisperLauncher,
            SubtitleToolsLauncher,
            Recordings,
        )
        holders.size
    }
}
