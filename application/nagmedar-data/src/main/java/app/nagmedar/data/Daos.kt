package app.nagmedar.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * DAO methods are blocking (callers move to Dispatchers.IO) and observation goes through Flow.
 * Room 2.6.1 under KSP2 cannot generate suspend DAO methods ("unexpected jvm signature V"),
 * and the upstream modules pin that Room version.
 */

/** S-1101: a subscription with its episodes counted, as the subscriptions' grid shows it. */
data class PodcastFeedSummary(
    @Embedded val feed: PodcastFeed,
    @ColumnInfo(name = "episode_count") val episodeCount: Int,
    /** Unplayed episodes a refresh added after the subscription. */
    @ColumnInfo(name = "new_count") val newCount: Int,
)

@Dao
abstract class PodcastDao {
    @Query("SELECT * FROM podcast_feed ORDER BY title COLLATE NOCASE")
    abstract fun feeds(): Flow<List<PodcastFeed>>

    @Query("SELECT * FROM podcast_feed ORDER BY title COLLATE NOCASE")
    abstract fun feedList(): List<PodcastFeed>

    @Query("SELECT * FROM podcast_feed WHERE id = :id")
    abstract fun feed(id: Long): PodcastFeed?

    @Query("SELECT * FROM podcast_feed WHERE id = :id")
    abstract fun observeFeed(id: Long): Flow<PodcastFeed?>

    /** S-1101: every subscription with its episode count and its new ones, by title. */
    @Query(
        "SELECT f.*, " +
            "(SELECT COUNT(*) FROM podcast_episode e WHERE e.feed_id = f.id) AS episode_count, " +
            "(SELECT COUNT(*) FROM podcast_episode e WHERE e.feed_id = f.id AND e.played = 0 AND e.added_at > f.subscribed_at) AS new_count " +
            "FROM podcast_feed f ORDER BY f.title COLLATE NOCASE",
    )
    abstract fun summaries(): Flow<List<PodcastFeedSummary>>

    @Query("SELECT * FROM podcast_feed WHERE feed_url = :url")
    abstract fun feedByUrl(url: String): PodcastFeed?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun subscribe(feed: PodcastFeed): Long

    @Update
    abstract fun updateFeed(feed: PodcastFeed)

    /** Removes the feed and, through the foreign key, its episodes. */
    @Delete
    abstract fun unsubscribe(feed: PodcastFeed)

    @Query("SELECT * FROM podcast_episode WHERE feed_id = :feedId ORDER BY published_at DESC")
    abstract fun episodes(feedId: Long): Flow<List<PodcastEpisode>>

    @Query("SELECT * FROM podcast_episode WHERE feed_id = :feedId ORDER BY published_at DESC")
    abstract fun episodeList(feedId: Long): List<PodcastEpisode>

    @Query("SELECT * FROM podcast_episode WHERE played = 0 ORDER BY published_at DESC LIMIT :limit")
    abstract fun newEpisodes(limit: Int): Flow<List<PodcastEpisode>>

    @Query("SELECT COUNT(*) FROM podcast_episode WHERE feed_id = :feedId AND played = 0")
    abstract fun unplayedCount(feedId: Long): Int

    /** New episodes are added, known ones (same feed and guid) keep their playback state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun addEpisodes(episodes: List<PodcastEpisode>): List<Long>

    /** S-1104: the playback position and whether the episode is played; the time it was marked played stays the first one. */
    @Query(
        "UPDATE podcast_episode SET position_ms = :positionMs, played = :played, " +
            "played_at = CASE WHEN :played = 0 THEN 0 WHEN played_at = 0 THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE played_at END " +
            "WHERE id = :episodeId",
    )
    abstract fun savePosition(episodeId: Long, positionMs: Long, played: Boolean)

    /** S-1104: only where the episode has got to; whether it is played stays as it is. */
    @Query("UPDATE podcast_episode SET position_ms = :positionMs WHERE id = :episodeId")
    abstract fun savePositionOnly(episodeId: Long, positionMs: Long)

    @Query(
        "UPDATE podcast_episode SET played = :played, " +
            "played_at = CASE WHEN :played = 0 THEN 0 WHEN played_at = 0 THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE played_at END " +
            "WHERE id = :episodeId",
    )
    abstract fun setPlayed(episodeId: Long, played: Boolean)

    @Query("SELECT * FROM podcast_episode WHERE id = :id")
    abstract fun episode(id: Long): PodcastEpisode?

    @Query("SELECT * FROM podcast_episode WHERE id = :id")
    abstract fun observeEpisode(id: Long): Flow<PodcastEpisode?>

    /** S-1104: the episode that plays from [location], its enclosure's address or its downloaded copy. */
    @Query("SELECT * FROM podcast_episode WHERE audio_url = :location OR download_path = :location ORDER BY id LIMIT 1")
    abstract fun episodeAt(location: String): PodcastEpisode?

    /** S-1103: the episodes in a download [state] (see [DownloadState]), the newest first. */
    @Query("SELECT * FROM podcast_episode WHERE download_state = :state ORDER BY published_at DESC")
    abstract fun episodesDownload(state: Int): List<PodcastEpisode>

    /** The Downloads page: the episodes being downloaded, waiting, done or failed, the newest first. */
    @Query("SELECT * FROM podcast_episode WHERE download_state IN (1, 2, 3, 4) ORDER BY download_state = 3, published_at DESC")
    abstract fun observeDownloads(): Flow<List<PodcastEpisode>>

    /** The downloaded episodes kept in a folder the system's picker gave (their documents): the folder's permission stays while they are. */
    @Query("SELECT download_path FROM podcast_episode WHERE download_path LIKE 'content:%'")
    abstract fun downloadedDocuments(): List<String>

    /** S-1103: where an episode's download is: its [state], and the file it is in once it is done. */
    @Query("UPDATE podcast_episode SET download_state = :state, download_path = :path, download_bytes = :bytes WHERE id = :episodeId")
    abstract fun setDownload(episodeId: Long, state: Int, path: String?, bytes: Long)

    /** S-1103: takes a queued download for the one that runs it; the number of rows taken (0 when it is not queued any more). */
    @Query("UPDATE podcast_episode SET download_state = 2 WHERE id = :episodeId AND download_state = 1")
    abstract fun claimDownload(episodeId: Long): Int

    /** S-1103: only the state, the file (if any) stays. */
    @Query("UPDATE podcast_episode SET download_state = :state WHERE id = :episodeId")
    abstract fun setDownloadState(episodeId: Long, state: Int)

    /** S-1103: the show notes of an episode a refresh listed again, when it has none yet (shows subscribed to before the notes were kept). */
    @Query("UPDATE podcast_episode SET notes_html = :html WHERE feed_id = :feedId AND guid = :guid AND notes_html IS NULL")
    abstract fun fillNotes(feedId: Long, guid: String, html: String)

    @Query("UPDATE podcast_feed SET notify = :on WHERE id = :feedId")
    abstract fun setNotify(feedId: Long, on: Boolean)

    /** S-1103: the show's automatic download: off, or the newest [count] of every refresh's new episodes. */
    @Query("UPDATE podcast_feed SET auto_download = :on, auto_download_count = :count WHERE id = :feedId")
    abstract fun setAutoDownload(feedId: Long, on: Boolean, count: Int)

    @Query("UPDATE podcast_feed SET keep_latest = :count WHERE id = :feedId")
    abstract fun setKeepLatest(feedId: Long, count: Int)

    /** S-1104: how the show's episodes play. */
    @Query("UPDATE podcast_feed SET speed = :speed, skip_intro_s = :intro, skip_outro_s = :outro WHERE id = :feedId")
    abstract fun setPlayback(feedId: Long, speed: Float, intro: Int, outro: Int)

    @Query("UPDATE podcast_feed SET speed = :speed WHERE id = :feedId")
    abstract fun setSpeed(feedId: Long, speed: Float)

    /** Version 16: the show's volume boost: 0 off, 1 light, 2 strong. */
    @Query("UPDATE podcast_feed SET volume_boost = :boost WHERE id = :feedId")
    abstract fun setVolumeBoost(feedId: Long, boost: Int)

    /** Version 21: the language Whisper hears the show in ([PodcastFeed.transcriptLanguage]). */
    @Query("UPDATE podcast_feed SET transcript_language = :language WHERE id = :feedId")
    abstract fun setTranscriptLanguage(feedId: Long, language: String?)

    /** Version 21: the episode's own transcript, written with [model]; null when it has none any more. The rows changed (0: the episode is gone). */
    @Query("UPDATE podcast_episode SET whisper_model = :model WHERE id = :episodeId")
    abstract fun setWhisperModel(episodeId: Long, model: String?): Int

    /** Version 21: the episodes that have a transcript of their own, by id. */
    @Query("SELECT id FROM podcast_episode WHERE whisper_model IS NOT NULL")
    abstract fun whisperEpisodes(): List<Long>

    /** S-1101: a new subscription with the episodes its first fetch read; the feed's id. */
    @Transaction
    open fun subscribeWith(feed: PodcastFeed, episodes: List<PodcastEpisode>): Long {
        val id = subscribe(feed)
        addEpisodes(episodes.map { it.copy(feedId = id) })
        return id
    }

    /**
     * Updates the show and adds the episodes it did not have; returns those, with their ids: the new ones. The show's own
     * settings are as they are in Room now, not as they were when the feed was fetched: a switch turned meanwhile stays.
     */
    @Transaction
    open fun refreshFeed(feed: PodcastFeed, episodes: List<PodcastEpisode>): List<PodcastEpisode> {
        val now = feed(feed.id)
        updateFeed(
            if (now == null) {
                feed
            } else {
                feed.copy(
                    autoDownload = now.autoDownload, notify = now.notify, autoDownloadCount = now.autoDownloadCount,
                    keepLatest = now.keepLatest, speed = now.speed, skipIntroSeconds = now.skipIntroSeconds, skipOutroSeconds = now.skipOutroSeconds,
                    volumeBoost = now.volumeBoost, transcriptLanguage = now.transcriptLanguage,
                )
            },
        )
        val rows = episodes.map { it.copy(feedId = feed.id) }
        val ids = addEpisodes(rows)
        // an episode listed again keeps its playback state; its notes are filled in when it has none
        rows.forEachIndexed { i, row -> if (ids[i] < 0) row.notesHtml?.let { fillNotes(feed.id, row.guid, it) } }
        return rows.indices.filter { ids[it] > 0 }.map { rows[it].copy(id = ids[it]) }
    }
}

@Dao
abstract class RadioDao {
    @Query("SELECT * FROM radio_station WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    abstract fun favorites(): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_station WHERE last_played_at > 0 ORDER BY last_played_at DESC LIMIT :limit")
    abstract fun recent(limit: Int): Flow<List<RadioStation>>

    /** The stations imported from playlist files: those without a directory UUID. */
    @Query("SELECT * FROM radio_station WHERE station_uuid IS NULL ORDER BY name COLLATE NOCASE")
    abstract fun imported(): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_station WHERE station_uuid = :uuid")
    abstract fun byUuid(uuid: String): RadioStation?

    @Query("SELECT * FROM radio_station WHERE id = :id")
    abstract fun byId(id: Long): RadioStation?

    @Query("SELECT * FROM radio_station WHERE station_uuid IN (:uuids)")
    abstract fun byUuids(uuids: List<String>): List<RadioStation>

    @Query("SELECT * FROM radio_station WHERE station_uuid IS NULL AND stream_url = :url LIMIT 1")
    abstract fun importedByUrl(url: String): RadioStation?

    @Upsert
    abstract fun save(station: RadioStation): Long

    /**
     * A station kept: one of the directory (same UUID) or an imported one (same address, no id yet)
     * already in the table is updated with what is known now and keeps the user's favourite, plays
     * and vote. Returns the row's id.
     */
    @Transaction
    open fun keep(station: RadioStation): Long {
        val known = station.stationUuid?.let(::byUuid)
            ?: station.takeIf { it.stationUuid == null && it.id == 0L }?.let { importedByUrl(it.streamUrl) }
        val row = if (known == null) station else station.copy(id = known.id, favorite = known.favorite, lastPlayedAt = known.lastPlayedAt, lastVotedAt = known.lastVotedAt)
        // an upsert that updates answers -1
        val inserted = save(row)
        return if (inserted > 0) inserted else row.id
    }

    @Query("UPDATE radio_station SET favorite = :favorite WHERE id = :id")
    abstract fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE radio_station SET last_played_at = :at WHERE id = :id")
    abstract fun markPlayed(id: Long, at: Long)

    @Query("UPDATE radio_station SET last_voted_at = :at, votes = :votes WHERE id = :id")
    abstract fun markVoted(id: Long, at: Long, votes: Int)

    /** Takes the station out of "recent" without touching its other uses. */
    @Query("UPDATE radio_station SET last_played_at = 0 WHERE id = :id")
    abstract fun forgetPlayed(id: Long)

    /** Drops rows nothing refers to any more: directory stations that are no favourite, were never played and never voted for. */
    @Query("DELETE FROM radio_station WHERE station_uuid IS NOT NULL AND favorite = 0 AND last_played_at = 0 AND last_voted_at = 0")
    abstract fun pruneUnused()

    @Delete
    abstract fun delete(station: RadioStation)
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE track_key = :trackKey ORDER BY synced DESC, fetched_at DESC")
    fun forTrack(trackKey: String): List<LyricsEntry>

    @Upsert
    fun save(entry: LyricsEntry)

    @Query("UPDATE lyrics_cache SET offset_ms = :offsetMs WHERE track_key = :trackKey AND source = :source")
    fun setOffset(trackKey: String, source: String, offsetMs: Long)

    /** Drops downloaded lyrics older than [before]; user edits and local files stay. */
    @Query("DELETE FROM lyrics_cache WHERE fetched_at < :before AND source = 'lrclib'")
    fun pruneDownloaded(before: Long): Int

    /** S-1003: how many tracks' downloaded lyrics are kept: found ones (an instrumental's word too), not the misses. */
    @Query("SELECT COUNT(*) FROM lyrics_cache WHERE source = 'lrclib' AND (text != '' OR language IS NOT NULL)")
    fun countDownloaded(): Int

    /** S-1502: what a backup keeps of the lyrics: the user's own, those given a delay, and (S-1005) the originals the editor replaced. */
    @Query("SELECT * FROM lyrics_cache WHERE source IN ('user', 'original_tag', 'original_file') OR offset_ms != 0 ORDER BY track_key, source")
    fun kept(): List<LyricsEntry>

    /** S-1502: a backup's lyrics, over what is here for the same track and source. */
    @Upsert
    fun saveAll(entries: List<LyricsEntry>)
}

@Dao
abstract class MediaSettingsDao {
    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    abstract fun get(uri: String): MediaSettings?

    /** S-1502: every file's, for a backup. */
    @Query("SELECT * FROM media_settings ORDER BY media_uri")
    abstract fun all(): List<MediaSettings>

    /** S-1502: a backup's rows, each over this one's when it is as new ([RestoreMerge.mediaSettings]). */
    @Transaction
    open fun restore(rows: List<MediaSettings>) {
        rows.forEach { row -> RestoreMerge.mediaSettings(get(row.mediaUri), row)?.let { save(it) } }
    }

    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    abstract fun observe(uri: String): Flow<MediaSettings?>

    @Upsert
    abstract fun save(settings: MediaSettings)

    @Query("DELETE FROM media_settings WHERE media_uri = :uri")
    abstract fun forget(uri: String)

    /** A row for [settings]' file, unless it has one: its values stay then. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun create(settings: MediaSettings)

    /** S-603: the effects panel's columns of [uri]'s row (null: not remembered); its delays and tracks stay. */
    @Query(
        "UPDATE media_settings SET aspect_ratio = :aspectRatio, deinterlace = :deinterlace, equalizer_preset = :equalizerPreset, " +
            "effects = :effects, updated_at = :updatedAt WHERE media_uri = :uri",
    )
    abstract fun updateEffects(uri: String, aspectRatio: String?, deinterlace: String?, equalizerPreset: String?, effects: String?, updatedAt: Long)

    /** S-603: remembers the effects panel's values for [uri], making its row when it has none. */
    @Transaction
    open fun saveEffects(uri: String, aspectRatio: String?, deinterlace: String?, equalizerPreset: String?, effects: String?, updatedAt: Long) {
        create(MediaSettings(uri, updatedAt = updatedAt))
        updateEffects(uri, aspectRatio, deinterlace, equalizerPreset, effects, updatedAt)
    }

    /** S-604: the sync panel's columns of [uri]'s row (null: not remembered); its effects and tracks stay. */
    @Query(
        "UPDATE media_settings SET audio_delay_ms = :audioDelayMs, subtitle_delay_ms = :subtitleDelayMs, " +
            "subtitle_rate = :subtitleRate, updated_at = :updatedAt WHERE media_uri = :uri",
    )
    abstract fun updateSync(uri: String, audioDelayMs: Long?, subtitleDelayMs: Long?, subtitleRate: String?, updatedAt: Long)

    /** S-604: remembers the sync panel's values for [uri], making its row when it has none. */
    @Transaction
    open fun saveSync(uri: String, audioDelayMs: Long?, subtitleDelayMs: Long?, subtitleRate: String?, updatedAt: Long) {
        create(MediaSettings(uri, updatedAt = updatedAt))
        updateSync(uri, audioDelayMs, subtitleDelayMs, subtitleRate, updatedAt)
    }

    /** S-703: the secondary subtitle track [uri] shows with its primary one (null: none); its other columns stay. */
    @Query("UPDATE media_settings SET secondary_subtitle_track = :track, updated_at = :updatedAt WHERE media_uri = :uri")
    abstract fun updateSecondarySubtitle(uri: String, track: String?, updatedAt: Long)

    /** S-703: remembers the secondary subtitle track for [uri], making its row when it has none. */
    @Transaction
    open fun saveSecondarySubtitle(uri: String, track: String?, updatedAt: Long) {
        create(MediaSettings(uri, updatedAt = updatedAt))
        updateSecondarySubtitle(uri, track, updatedAt)
    }

    /** S-702: the character set [uri]'s subtitles are read in (null: detected); its other columns stay. */
    @Query("UPDATE media_settings SET subtitle_encoding = :encoding, updated_at = :updatedAt WHERE media_uri = :uri")
    abstract fun updateSubtitleEncoding(uri: String, encoding: String?, updatedAt: Long)

    /** S-702: remembers the subtitles' character set for [uri], making its row when it has none. */
    @Transaction
    open fun saveSubtitleEncoding(uri: String, encoding: String?, updatedAt: Long) {
        create(MediaSettings(uri, updatedAt = updatedAt))
        updateSubtitleEncoding(uri, encoding, updatedAt)
    }
}

@Dao
interface MediaTechDao {
    @Query("SELECT * FROM media_tech")
    fun all(): Flow<List<MediaTechEntry>>

    @Query("SELECT * FROM media_tech WHERE media_uri = :uri")
    fun get(uri: String): MediaTechEntry?

    @Upsert
    fun save(entry: MediaTechEntry)

    @Query("DELETE FROM media_tech WHERE media_uri = :uri")
    fun forget(uri: String)
}

@Dao
interface AudioTechDao {
    @Query("SELECT * FROM audio_tech")
    fun all(): List<AudioTechEntry>

    @Query("SELECT * FROM audio_tech WHERE media_uri = :uri")
    fun get(uri: String): AudioTechEntry?

    @Upsert
    fun save(entries: List<AudioTechEntry>)

    @Query("DELETE FROM audio_tech WHERE media_uri = :uri")
    fun forget(uri: String)
}

@Dao
abstract class TrackStatsDao {
    @Query("SELECT * FROM track_stats")
    abstract fun observe(): Flow<List<TrackStatsEntry>>

    @Query("SELECT * FROM track_stats WHERE track_key = :key")
    abstract fun get(key: String): TrackStatsEntry?

    /** S-1502: every track's, for a backup. */
    @Query("SELECT * FROM track_stats ORDER BY track_key")
    abstract fun all(): List<TrackStatsEntry>

    /** [entry] as it is. */
    @Upsert
    abstract fun save(entry: TrackStatsEntry)

    /** S-1502: a backup's statistics, merged with this one's ([RestoreMerge.stats]). */
    @Transaction
    open fun restore(rows: List<TrackStatsEntry>) {
        rows.forEach { row -> save(RestoreMerge.stats(get(row.trackKey), row)) }
    }

    /** A row for [key], unless it has one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun create(entry: TrackStatsEntry)

    @Query(
        "UPDATE track_stats SET play_count = play_count + 1, last_played_at = :at, updated_at = :at, " +
            "first_played_at = CASE WHEN first_played_at = 0 THEN :at ELSE first_played_at END WHERE track_key = :key",
    )
    abstract fun addPlay(key: String, at: Long)

    @Query("UPDATE track_stats SET skip_count = skip_count + 1, updated_at = :at WHERE track_key = :key")
    abstract fun addSkip(key: String, at: Long)

    @Query("UPDATE track_stats SET rating = :rating, updated_at = :at WHERE track_key = :key")
    abstract fun updateRating(key: String, rating: Int, at: Long)

    /** A play of [key] counted at [at]. */
    @Transaction
    open fun played(key: String, at: Long) {
        create(TrackStatsEntry(key, updatedAt = at))
        addPlay(key, at)
    }

    /** [key] left for another track before it counted as played. */
    @Transaction
    open fun skipped(key: String, at: Long) {
        create(TrackStatsEntry(key, updatedAt = at))
        addSkip(key, at)
    }

    /** [key]'s rating: 0 (none) to 5. */
    @Transaction
    open fun rate(key: String, rating: Int, at: Long) {
        create(TrackStatsEntry(key, updatedAt = at))
        updateRating(key, rating.coerceIn(0, 5), at)
    }
}

@Dao
interface SmartPlaylistDao {
    /** In the order they were made. */
    @Query("SELECT * FROM smart_playlist ORDER BY created_at, id")
    fun observe(): Flow<List<SmartPlaylistEntry>>

    @Query("SELECT * FROM smart_playlist WHERE id = :id")
    fun get(id: Long): SmartPlaylistEntry?

    /** S-1502: every list, in the order they were made, for a backup. */
    @Query("SELECT * FROM smart_playlist ORDER BY created_at, id")
    fun all(): List<SmartPlaylistEntry>

    @Insert
    fun insert(entry: SmartPlaylistEntry): Long

    @Update
    fun update(entry: SmartPlaylistEntry)

    @Query("DELETE FROM smart_playlist WHERE id = :id")
    fun delete(id: Long)
}

/** S-613: the output devices' latency profiles. */
@Dao
abstract class LatencyProfileDao {
    /** Every device seen, by kind of device and name: what the settings list. */
    @Query("SELECT * FROM latency_profile ORDER BY device_type, device_name, device_address")
    abstract fun observe(): Flow<List<LatencyProfileEntry>>

    /** S-1502: every profile, for a backup; in the list's order. */
    @Query("SELECT * FROM latency_profile ORDER BY device_type, device_name, device_address")
    abstract fun all(): List<LatencyProfileEntry>

    @Query("SELECT * FROM latency_profile WHERE device_type = :type AND device_name = :name AND device_address = :address")
    abstract fun get(type: Int, name: String, address: String): LatencyProfileEntry?

    /** [entry] as it is, over the device's old row. */
    @Upsert
    abstract fun save(entry: LatencyProfileEntry)

    /** A row for a device seen, unless it has one: the list shows it at 0 until it is set. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun seen(entry: LatencyProfileEntry)

    @Query("DELETE FROM latency_profile WHERE device_type = :type AND device_name = :name AND device_address = :address")
    abstract fun delete(type: Int, name: String, address: String)

    /** S-1502: a backup's profiles over these; a device's row is replaced by the backup's. */
    @Transaction
    open fun restore(rows: List<LatencyProfileEntry>) {
        rows.forEach(::save)
    }
}

/** S-1301: the network servers the user saved. */
@Dao
interface NetworkServerDao {
    /** By name, again after each change. */
    @Query("SELECT * FROM network_server ORDER BY name COLLATE NOCASE, id")
    fun observe(): Flow<List<NetworkServerEntry>>

    @Query("SELECT * FROM network_server ORDER BY name COLLATE NOCASE, id")
    fun all(): List<NetworkServerEntry>

    @Query("SELECT * FROM network_server WHERE id = :id")
    fun get(id: Long): NetworkServerEntry?

    @Query("SELECT * FROM network_server WHERE url = :url")
    fun byUrl(url: String): NetworkServerEntry?

    @Insert
    fun insert(entry: NetworkServerEntry): Long

    @Update
    fun update(entry: NetworkServerEntry)

    /** Its sign-in sealed anew, or forgotten (null). */
    @Query("UPDATE network_server SET credentials = :credentials WHERE id = :id")
    fun setCredentials(id: Long, credentials: ByteArray?)

    @Query("UPDATE network_server SET last_used_at = :at WHERE id = :id")
    fun used(id: Long, at: Long)

    @Query("DELETE FROM network_server WHERE id = :id")
    fun delete(id: Long)
}

@Dao
interface ScrobbleDao {
    /** [entry] queued; -1 when the same track at the same time is already waiting for the service. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun add(entry: ScrobbleEntry): Long

    /** The oldest [limit] scrobbles waiting for [service]. */
    @Query("SELECT * FROM scrobble_queue WHERE service = :service ORDER BY listened_at, id LIMIT :limit")
    fun pending(service: String, limit: Int): List<ScrobbleEntry>

    /** Sent, or given up on. */
    @Query("DELETE FROM scrobble_queue WHERE id IN (:ids)")
    fun remove(ids: List<Long>)

    /** Sending them failed for a reason that may pass. */
    @Query("UPDATE scrobble_queue SET attempts = attempts + 1 WHERE id IN (:ids)")
    fun failed(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM scrobble_queue WHERE service = :service")
    fun count(service: String): Int

    /** How many wait for each service, as they change. */
    @Query("SELECT service, COUNT(*) AS count FROM scrobble_queue GROUP BY service")
    fun counts(): Flow<List<ScrobbleCount>>

    /** Forgets what waits for [service]; how many. */
    @Query("DELETE FROM scrobble_queue WHERE service = :service")
    fun clear(service: String): Int

    /** Forgets what was heard before [before] (seconds since the epoch) and waits for [service]; how many. */
    @Query("DELETE FROM scrobble_queue WHERE service = :service AND listened_at < :before")
    fun dropOlderThan(service: String, before: Long): Int
}

/** S-1303: the user's video conversion profiles, by name. */
@Dao
interface TranscodeProfileDao {
    @Query("SELECT * FROM transcode_profile ORDER BY name COLLATE NOCASE, id")
    fun observe(): Flow<List<TranscodeProfileEntry>>

    @Query("SELECT * FROM transcode_profile ORDER BY name COLLATE NOCASE, id")
    fun all(): List<TranscodeProfileEntry>

    @Query("SELECT * FROM transcode_profile WHERE id = :id")
    fun get(id: Long): TranscodeProfileEntry?

    @Insert
    fun insert(entry: TranscodeProfileEntry): Long

    @Update
    fun update(entry: TranscodeProfileEntry)

    @Query("DELETE FROM transcode_profile WHERE id = :id")
    fun delete(id: Long)
}

/** S-1303: the video conversions' queue: the oldest waiting runs first, the newest shows first. */
@Dao
interface TranscodeJobDao {
    @Query("SELECT * FROM transcode_job ORDER BY created_at DESC, id DESC")
    fun observe(): Flow<List<TranscodeJobEntry>>

    @Query("SELECT * FROM transcode_job WHERE id = :id")
    fun get(id: Long): TranscodeJobEntry?

    /** The next job to run: the oldest waiting. */
    @Query("SELECT * FROM transcode_job WHERE state = 'queued' ORDER BY created_at, id LIMIT 1")
    fun next(): TranscodeJobEntry?

    /** The jobs a process that died left running or paused. */
    @Query("SELECT * FROM transcode_job WHERE state IN ('running', 'paused')")
    fun interrupted(): List<TranscodeJobEntry>

    /** How many jobs wait or run. */
    @Query("SELECT COUNT(*) FROM transcode_job WHERE state IN ('queued', 'running', 'paused')")
    fun pending(): Int

    @Insert
    fun insert(job: TranscodeJobEntry): Long

    @Update
    fun update(job: TranscodeJobEntry)

    @Query("UPDATE transcode_job SET state = :state, error = :error WHERE id = :id")
    fun saveState(id: Long, state: String, error: String?)

    @Query("UPDATE transcode_job SET progress = :progress, media_ms = :mediaMs, total_ms = :totalMs, bytes_out = :bytesOut WHERE id = :id")
    fun saveProgress(id: Long, progress: Float, mediaMs: Long, totalMs: Long, bytesOut: Long)

    /** The job found its output empty and writes it: its to empty or remove from now on. */
    @Query("UPDATE transcode_job SET owns_output = 1 WHERE id = :id")
    fun ownOutput(id: Long)

    /** Asks a job that waits or runs to stop; 1 when it was asked. */
    @Query("UPDATE transcode_job SET cancel_requested = 1 WHERE id = :id AND state IN ('queued', 'running', 'paused')")
    fun requestCancel(id: Long): Int

    /** Cancels a job that still waits (the worker may have taken it up since); 1 when it did. */
    @Query("UPDATE transcode_job SET state = 'cancelled', error = 'cancelled', finished_at = :at, cancel_requested = 0 WHERE id = :id AND state = 'queued'")
    fun cancelQueued(id: Long, at: Long): Int

    /** Removes a finished job's row; a job that waits or runs stays. */
    @Query("DELETE FROM transcode_job WHERE id = :id AND state IN ('done', 'failed', 'cancelled')")
    fun deleteFinished(id: Long): Int
}

/** S-1107: the tracks radio recordings were cut into, and the recordings scheduled. */
@Dao
abstract class RadioRecordingDao {
    /** The tracks kept, newest first; the ones in the bin are left out. */
    @Query("SELECT * FROM radio_recording WHERE trashed_at = 0 ORDER BY session_started_at DESC, started_at ASC, id ASC")
    abstract fun tracks(): Flow<List<RadioRecording>>

    @Query("SELECT * FROM radio_recording WHERE id = :id")
    abstract fun track(id: Long): RadioRecording?

    @Query("SELECT * FROM radio_recording WHERE id IN (:ids)")
    abstract fun byIds(ids: List<Long>): List<RadioRecording>

    @Query("SELECT * FROM radio_recording WHERE session_id = :sessionId ORDER BY started_at ASC, id ASC")
    abstract fun sessionTracks(sessionId: String): List<RadioRecording>

    @Query("SELECT * FROM radio_recording WHERE trashed_at > 0")
    abstract fun trashed(): List<RadioRecording>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun add(track: RadioRecording): Long

    /** What the tracks kept take, bytes; the bin's too, since the files are still there. */
    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM radio_recording")
    abstract fun usedBytes(): Long

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM radio_recording")
    abstract fun used(): Flow<Long>

    /** The oldest tracks first (the bin's, then the sessions that began first), for pruning. */
    @Query("SELECT * FROM radio_recording ORDER BY (trashed_at > 0) DESC, session_started_at ASC, started_at ASC, id ASC")
    abstract fun oldestFirst(): List<RadioRecording>

    @Query("UPDATE radio_recording SET file_uri = :uri, file_name = :name WHERE id = :id")
    abstract fun rename(id: Long, uri: String, name: String)

    @Query("UPDATE radio_recording SET trashed_at = :at WHERE id IN (:ids)")
    abstract fun trash(ids: List<Long>, at: Long)

    @Query("DELETE FROM radio_recording WHERE id IN (:ids)")
    abstract fun delete(ids: List<Long>)

    @Query("DELETE FROM radio_recording")
    abstract fun clear()

    @Query("SELECT * FROM radio_schedule ORDER BY hour, minute, id")
    abstract fun schedules(): Flow<List<RadioScheduleEntry>>

    @Query("SELECT * FROM radio_schedule ORDER BY id")
    abstract fun scheduleList(): List<RadioScheduleEntry>

    @Query("SELECT * FROM radio_schedule WHERE id = :id")
    abstract fun schedule(id: Long): RadioScheduleEntry?

    @Upsert
    abstract fun saveSchedule(entry: RadioScheduleEntry): Long

    /** Saves a schedule and returns its id (an upsert that updates answers -1). */
    @Transaction
    open fun keepSchedule(entry: RadioScheduleEntry): Long {
        val inserted = saveSchedule(entry)
        return if (inserted > 0) inserted else entry.id
    }

    @Query("UPDATE radio_schedule SET enabled = :enabled WHERE id = :id")
    abstract fun enableSchedule(id: Long, enabled: Boolean)

    @Query("UPDATE radio_schedule SET last_started_at = :at WHERE id = :id")
    abstract fun markScheduleStarted(id: Long, at: Long)

    @Query("UPDATE radio_schedule SET checked_at = :at WHERE id = :id")
    abstract fun markScheduleChecked(id: Long, at: Long)

    @Query("DELETE FROM radio_schedule WHERE id = :id")
    abstract fun deleteSchedule(id: Long)

    // --- the runs of the schedules

    /** The runs of every schedule, the latest window first. */
    @Query("SELECT * FROM radio_schedule_run ORDER BY window_start DESC, id DESC")
    abstract fun runs(): Flow<List<RadioScheduleRun>>

    @Query("SELECT * FROM radio_schedule_run WHERE schedule_id = :scheduleId AND window_start = :windowStart")
    abstract fun run(scheduleId: Long, windowStart: Long): RadioScheduleRun?

    /** Runs a restart cut off whose window is not over at [now]: they are begun again. */
    @Query("SELECT * FROM radio_schedule_run WHERE status = 'Interrupted' AND window_end > :now ORDER BY window_start")
    abstract fun resumable(now: Long): List<RadioScheduleRun>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertRun(run: RadioScheduleRun): Long

    @Query("UPDATE radio_schedule_run SET status = :status, window_end = :windowEnd, session_id = :sessionId, updated_at = :at WHERE id = :id")
    abstract fun updateRun(id: Long, status: String, windowEnd: Long, sessionId: String?, at: Long)

    @Query("UPDATE radio_schedule_run SET status = :status, updated_at = :at WHERE session_id = :sessionId AND status = 'Running'")
    abstract fun endRunsOf(sessionId: String, status: String, at: Long)

    /** Every run that was going on is cut off (the app began again, so nothing is recording): it is Interrupted, once more. */
    @Query("UPDATE radio_schedule_run SET status = 'Interrupted', interruptions = interruptions + 1, updated_at = :at WHERE status = 'Running'")
    abstract fun interruptRunning(at: Long)

    @Query(
        "DELETE FROM radio_schedule_run WHERE schedule_id = :scheduleId AND id NOT IN " +
            "(SELECT id FROM radio_schedule_run WHERE schedule_id = :scheduleId ORDER BY window_start DESC LIMIT :keep)",
    )
    abstract fun trimRuns(scheduleId: Long, keep: Int)

    @Query("DELETE FROM radio_schedule_run WHERE schedule_id = :scheduleId")
    abstract fun deleteRuns(scheduleId: Long)

    /** The run of [scheduleId]'s window as [status] (a row is made when there is none; the interruptions stay). */
    @Transaction
    open fun keepRun(scheduleId: Long, windowStart: Long, windowEnd: Long, status: String, sessionId: String?, at: Long) {
        val same = run(scheduleId, windowStart)
        if (same == null) {
            insertRun(RadioScheduleRun(scheduleId = scheduleId, windowStart = windowStart, windowEnd = windowEnd, status = status, sessionId = sessionId, updatedAt = at))
            trimRuns(scheduleId, KEPT_RUNS)
        } else {
            updateRun(same.id, status, windowEnd, sessionId, at)
        }
    }

    /** A window nobody recorded: a row says Missed, unless the window has a row already. */
    @Transaction
    open fun keepMissed(scheduleId: Long, windowStart: Long, windowEnd: Long, at: Long) {
        if (run(scheduleId, windowStart) != null) return
        insertRun(RadioScheduleRun(scheduleId = scheduleId, windowStart = windowStart, windowEnd = windowEnd, status = "Missed", updatedAt = at))
        trimRuns(scheduleId, KEPT_RUNS)
    }

    companion object {
        /** How many runs of a schedule are kept. */
        const val KEPT_RUNS = 30
    }
}

/** S-1206: the remux queue, newest first; the worker takes the first waiting job in the queue's order (S-1210). */
@Dao
interface RemuxJobDao {
    @Query("SELECT * FROM remux_job ORDER BY created_at DESC, id DESC")
    fun observe(): Flow<List<RemuxJobEntry>>

    /** The jobs of one film, newest first. */
    @Query("SELECT * FROM remux_job WHERE input_uri = :inputUri ORDER BY created_at DESC, id DESC")
    fun observeInput(inputUri: String): Flow<List<RemuxJobEntry>>

    @Query("SELECT * FROM remux_job WHERE id = :id")
    fun get(id: Long): RemuxJobEntry?

    @Query("SELECT * FROM remux_job ORDER BY created_at DESC, id DESC")
    fun all(): List<RemuxJobEntry>

    /** The next job to run: the first waiting in the queue's order (S-1210), then the oldest. */
    @Query("SELECT * FROM remux_job WHERE state = 'queued' ORDER BY position, created_at, id LIMIT 1")
    fun next(): RemuxJobEntry?

    /** S-1210: the place after the last job, for a new one. */
    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM remux_job")
    fun nextPosition(): Long

    /** S-1210: job [id] moved to [position] in the queue. */
    @Query("UPDATE remux_job SET position = :position WHERE id = :id")
    fun savePosition(id: Long, position: Long)

    /** Jobs left running, paused or verifying by a process that died; they start again. */
    @Query("SELECT * FROM remux_job WHERE state IN ('running', 'paused', 'verifying') ORDER BY created_at, id")
    fun interrupted(): List<RemuxJobEntry>

    @Insert
    fun insert(entry: RemuxJobEntry): Long

    /** The whole row; a cancel asked meanwhile would be lost, so the worker writes columns while a job runs. */
    @Update
    fun update(entry: RemuxJobEntry)

    /** How far a running job got. */
    @Query("UPDATE remux_job SET progress = :progress, media_seconds = :mediaSeconds, total_seconds = :totalSeconds, bytes_out = :bytesOut WHERE id = :id")
    fun saveProgress(id: Long, progress: Float, mediaSeconds: Double, totalSeconds: Double, bytesOut: Long)

    /** A job's state, why, and what happened; cancel_requested stays as it is. */
    @Query("UPDATE remux_job SET state = :state, error = :error, events = :events WHERE id = :id")
    fun saveState(id: Long, state: String, error: String?, events: String)

    /**
     * Job [id] taken up, its attempt and start, while it still waits: 1, or 0 when it was
     * cancelled (or taken) meanwhile and must be left alone. cancel_requested stays as it is.
     */
    @Query("UPDATE remux_job SET state = 'running', error = NULL, progress = 0, attempts = :attempts, started_at = :startedAt, finished_at = NULL, events = :events WHERE id = :id AND state = 'queued'")
    fun claim(id: Long, attempts: Int, startedAt: Long, events: String): Int

    /** A waiting job marked with why it waits ([error]); 0 rows when it no longer waits. */
    @Query("UPDATE remux_job SET error = :error WHERE id = :id AND state = 'queued'")
    fun markWaiting(id: Long, error: String): Int

    /** A job a dead process left running, paused or verifying, queued again; 0 rows when it is in no such state now. */
    @Query("UPDATE remux_job SET state = 'queued', progress = 0, media_seconds = 0, bytes_out = 0, error = NULL, events = :events WHERE id = :id AND state IN ('running', 'paused', 'verifying')")
    fun requeueInterrupted(id: Long, events: String): Int

    /** Failed or cancelled jobs whose unfinished output could not be removed yet: removed when the queue next runs. */
    @Query("SELECT * FROM remux_job WHERE owns_output = 1 AND state IN ('failed', 'cancelled') ORDER BY created_at, id")
    fun leftovers(): List<RemuxJobEntry>

    /** How many jobs but [id] use [outputUri]: one not finished, or one whose file it is (done, or left to remove). */
    @Query("SELECT COUNT(*) FROM remux_job WHERE output_uri = :outputUri AND id != :id AND (state IN ('queued', 'running', 'paused', 'verifying') OR owns_output = 1)")
    fun othersUsing(outputUri: String, id: Long): Int

    /** The remux done, the verification next: the engine's stats kept. */
    @Query("UPDATE remux_job SET state = :state, progress = 0, media_seconds = :mediaSeconds, total_seconds = :totalSeconds, bytes_out = :bytesOut, result = :result WHERE id = :id")
    fun saveResult(id: Long, state: String, mediaSeconds: Double, totalSeconds: Double, bytesOut: Long, result: String)

    /** The job found its output empty and writes it: its to remove from now on. */
    @Query("UPDATE remux_job SET owns_output = :owns WHERE id = :id")
    fun saveOwnsOutput(id: Long, owns: Boolean)

    @Query("UPDATE remux_job SET cancel_requested = 1 WHERE id = :id")
    fun requestCancel(id: Long)

    /** A waiting job cancelled; 0 rows when the worker took it up meanwhile (then [requestCancel]). */
    @Query("UPDATE remux_job SET state = 'cancelled', error = :error, finished_at = :at, cancel_requested = 0 WHERE id = :id AND state = 'queued'")
    fun cancelQueued(id: Long, error: String, at: Long): Int

    @Query("DELETE FROM remux_job WHERE id = :id")
    fun delete(id: Long)
}

/** Film information: the films' and series' TMDB details and their episodes, a cache the user can clear. */
@Dao
abstract class FilmInfoDao {
    @Query("SELECT * FROM film_info")
    abstract fun observeAll(): Flow<List<FilmInfoEntry>>

    @Query("SELECT * FROM film_info WHERE query_key = :key")
    abstract fun get(key: String): FilmInfoEntry?

    @Query("SELECT * FROM film_info")
    abstract fun all(): List<FilmInfoEntry>

    @Upsert
    abstract fun save(entry: FilmInfoEntry)

    @Query("SELECT COUNT(*) FROM film_info WHERE tmdb_id != 0")
    abstract fun observeFound(): Flow<Int>

    @Query("SELECT * FROM film_episode WHERE show_id = :showId ORDER BY season, episode")
    abstract fun observeEpisodes(showId: Long): Flow<List<FilmEpisodeEntry>>

    @Query("SELECT season FROM film_episode WHERE show_id = :showId GROUP BY season")
    abstract fun seasons(showId: Long): List<Int>

    /** The seasons of [showId] TMDB had no episodes of ([FilmEpisodeEntry.NO_EPISODES] alone), last asked before [before]. */
    @Query("SELECT season FROM film_episode WHERE show_id = :showId GROUP BY season HAVING MAX(episode) < 0 AND MAX(fetched_at) < :before")
    abstract fun emptySeasonsAskedBefore(showId: Long, before: Long): List<Int>

    @Query("SELECT DISTINCT language FROM film_episode WHERE show_id = :showId")
    abstract fun episodeLanguages(showId: Long): List<String>

    @Upsert
    abstract fun saveEpisodes(entries: List<FilmEpisodeEntry>)

    @Query("DELETE FROM film_episode WHERE show_id = :showId")
    abstract fun clearEpisodes(showId: Long)

    @Query("DELETE FROM film_info")
    abstract fun clearInfo()

    @Query("DELETE FROM film_episode")
    abstract fun clearAllEpisodes()

    /** Everything found: the next visit of the library looks it up again. */
    @Transaction
    open fun clear() {
        clearAllEpisodes()
        clearInfo()
    }
}
