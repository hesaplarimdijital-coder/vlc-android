package app.nagmedar.ui.podcast

import android.content.Context
import android.net.Uri
import app.nagmedar.data.NagmedarDatabase
import app.nagmedar.data.PodcastDao
import app.nagmedar.data.PodcastEpisode
import app.nagmedar.playback.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.AppScope
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.vlc.media.ResumeHook
import org.videolan.vlc.media.VolumeRestorer
import org.videolan.vlc.media.ResumeOwner

/**
 * S-1104: what the playback service plays, followed for as long as it lives, whatever started it: when it is a podcast
 * episode (by its address, the enclosure's or the downloaded file's), the show's speed is set, the intro skipped, the
 * place kept every few seconds and when it pauses or stops, the episode marked played at its end, and the next one started
 * when the show's outro begins, and the show's volume boost applied for as long as its episode plays. The rules are
 * [EpisodeTracker]'s; this looks, asks Room, and does what it says.
 * A look a second while it plays, every few seconds while it does not.
 */
class PodcastPlaybackObserver : PlaybackObserver {
    private var job: Job? = null
    private var dao: PodcastDao? = null
    private val tracker = EpisodeTracker()
    private val booster = VolumeBooster<PlaybackEngine>({ it.getVolume() }, { engine, volume -> engine.setVolume(volume) })

    override fun attach(service: PlaybackService) {
        val app = service.applicationContext
        val dao = NagmedarDatabase.get(app).podcasts().also { this.dao = it }
        // an episode follows its show's resume rule, not VLC's "resume playback" setting and its dialog
        ResumeHook.owner = ResumeOwner { location -> episodeAt(dao, location) != null }
        // the playlist gives the volume back before any other media starts: nothing but the episode plays boosted; the
        // bars and the volume keys show and set the listener's own volume, the boost goes on over it
        ResumeHook.volume = object : VolumeRestorer {
            override fun restore() {
                runCatching { booster.end() }
            }

            override fun listenerVolume(engine: Int): Int = booster.listenerVolume(engine)

            override fun engineVolume(volume: Int): Int = booster.listenerSets(volume)
        }
        job?.cancel()
        job = AppScope.launch {
            var asked: String? = null
            var context: EpisodeContext? = null
            var playing = false
            var looks = 0
            while (isActive) {
                delay(if (playing) SAMPLE_MS else IDLE_MS)
                playing = runCatching { service.isPlaying }.getOrDefault(false)
                val location = runCatching { service.currentMediaLocation }.getOrNull()
                if (location != asked) {
                    asked = location
                    context = location?.let { withContext(Dispatchers.IO) { contextOf(dao, it) } }
                    publish(app, context)
                } else if (context != null && location != null && ++looks % REFRESH_LOOKS == 0) {
                    // the show's settings may have changed meanwhile (its boost, its skips): they apply to this episode too
                    context = withContext(Dispatchers.IO) { contextOf(dao, location) }
                }
                val commands = tracker.sample(
                    location, context, service.getTime(), service.length, playing, service.rate, System.currentTimeMillis(),
                )
                apply(service, dao, commands)
                // while another media is being started the volume is the listener's; the boost waits for the episode
                if (!ResumeHook.switching) {
                    runCatching { booster.update(service.playlistManager.engine, context?.episodeId, context?.volumeBoost ?: VolumeBoost.OFF) }
                }
            }
        }
    }

    override fun detach() {
        job?.cancel()
        job = null
        ResumeHook.owner = null
        ResumeHook.volume = null
        runCatching { booster.end() }
        // the place of what was playing is kept as the service goes
        val saves = tracker.end().filterIsInstance<TrackerCommand.SavePosition>()
        val dao = dao
        if (dao != null && saves.isNotEmpty()) {
            AppScope.launch(Dispatchers.IO) { saves.forEach { dao.savePosition(it.episodeId, it.positionMs, it.played) } }
        }
        PodcastCenter.setPlaying(null)
    }

    private suspend fun publish(app: Context, context: EpisodeContext?) {
        PodcastCenter.setPlaying(context?.let { PlayingEpisode(it.episodeId, it.feedId) })
        if (context == null) return
        val episode = withContext(Dispatchers.IO) { NagmedarDatabase.get(app).podcasts().episode(context.episodeId) } ?: return
        val chapters = (PodcastCenter.extras(app).chapters(episode) as? ExtraLoad.Ready)?.value.orEmpty()
        // only if it is still the one that plays
        if (chapters.isNotEmpty() && PodcastCenter.playing.value?.episodeId == context.episodeId) {
            PodcastCenter.setPlaying(PlayingEpisode(context.episodeId, context.feedId, chapters))
        }
    }

    private suspend fun apply(service: PlaybackService, dao: PodcastDao, commands: List<TrackerCommand>) {
        for (command in commands) {
            when (command) {
                is TrackerCommand.SetRate -> service.setRate(command.rate, false)
                is TrackerCommand.SeekTo -> service.seek(command.ms)
                is TrackerCommand.SavePosition -> withContext(Dispatchers.IO) { dao.savePosition(command.episodeId, command.positionMs, command.played) }
                is TrackerCommand.SaveSpeed -> withContext(Dispatchers.IO) { dao.setSpeed(command.feedId, command.speed) }
                TrackerCommand.Finish -> if (service.hasNext()) service.next() else service.stop()
            }
        }
    }

    companion object {
        private const val SAMPLE_MS = 1_000L
        private const val IDLE_MS = 4_000L

        /** How many looks between re-reading the playing show's settings. */
        const val REFRESH_LOOKS = 5

        /** The episode [location] is the audio of, with what its show asks of its playback; null for any other media. */
        private fun contextOf(dao: PodcastDao, location: String): EpisodeContext? {
            val episode = episodeAt(dao, location) ?: return null
            val feed = dao.feed(episode.feedId) ?: return null
            return EpisodeContext(
                episodeId = episode.id,
                feedId = feed.id,
                speed = feed.speed,
                introSeconds = feed.skipIntroSeconds,
                outroSeconds = feed.skipOutroSeconds,
                volumeBoost = VolumeBoost.clamp(feed.volumeBoost),
                positionMs = episode.positionMs,
                played = episode.played,
                durationMs = episode.durationMs,
            )
        }

        /**
         * The episode at [location]: the enclosure's address matches as it is; a downloaded file plays from a `file:`
         * address of the path Room keeps. Also the resume question's (an episode is never asked). Off the main thread.
         */
        internal fun episodeAt(dao: PodcastDao, location: String): PodcastEpisode? {
            dao.episodeAt(location)?.let { return it }
            if (location.startsWith("file:")) Uri.parse(location).path?.let { path -> dao.episodeAt(path)?.let { return it } }
            return null
        }
    }
}
