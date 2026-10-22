package app.nagmedar.ui.podcast

import android.content.res.Resources
import app.nagmedar.ui.R
import app.nagmedar.ui.playback.PlaybackActions
import org.videolan.vlc.PlaybackService

/** S-1104: what stepping to another chapter of the playing episode came to. */
sealed interface ChapterStep {
    /** The media playing has no chapters (or nothing plays). */
    data object NoChapters : ChapterStep

    /** The last chapter is playing: there is no next one. */
    data object AtEnd : ChapterStep

    /** The player is at the start of chapter [index] (from 0). */
    data class Went(val index: Int, val chapter: Chapter) : ChapterStep
}

/**
 * S-1104: the chapter actions of the playing podcast episode, shared by every place it plays: the shell's shortcuts
 * (Ctrl+Alt+→ and ←), the buttons of the bars and the panels, the video player's keys and controls.
 */
object ChapterSteps {
    /** The chapter's name, or "3. kısım" when the file names none. */
    fun title(res: Resources, index: Int, chapter: Chapter): String = chapter.title.ifBlank { res.getString(R.string.nag_pod_chapter_n, index + 1) }

    /** The next chapter, or for [forward] false the start of this one (the one before when it began just now), and the player is there. */
    fun step(forward: Boolean): ChapterStep {
        val chapters = PodcastCenter.playing.value?.chapters.orEmpty()
        if (chapters.isEmpty()) return ChapterStep.NoChapters
        val service = PlaybackService.instance ?: return ChapterStep.NoChapters
        val index = Chapters.step(chapters, service.getTime(), forward) ?: return ChapterStep.AtEnd
        PlaybackActions.seekToMs(chapters[index].startMs)
        return ChapterStep.Went(index, chapters[index])
    }
}
