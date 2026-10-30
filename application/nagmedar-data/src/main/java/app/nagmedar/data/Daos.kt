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

/** 1.0: an episode's title and where it belongs, without the rest of its row (its notes): what Android Auto's search reads of every episode. */
data class PodcastEpisodeTitle(
    val id: Long,
    @ColumnInfo(name = "feed_id") val feedId: Long,
    val title: String,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
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

    /** A refresh the server answered "not modified": the time it was read, and nothing else (a choice made meanwhile stays). */
    @Query("UPDATE podcast_feed SET refreshed_at = :at WHERE id = :feedId")
    abstract fun setRefreshedAt(feedId: Long, at: Long)

    /** A "not modified" refresh of a feed that moved for good: its new address, the old ones it answers to, the time it was read. */
    @Query("UPDATE podcast_feed SET feed_url = :url, alias_keys = :aliasKeys, refreshed_at = :at WHERE id = :feedId")
    abstract fun setMovedRefreshedAt(feedId: Long, url: String, aliasKeys: String, at: Long)

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

    /** 1.0: the episodes left in the middle, not played and with a place kept (Android Auto's "Devam eden bölümler"). */
    @Query("SELECT * FROM podcast_episode WHERE played = 0 AND position_ms > 0")
    abstract fun inProgress(): List<PodcastEpisode>

    /** 1.0: the new episodes, unplayed and added by a refresh after the subscription, the newest first (Android Auto's "Yeni bölümler"). */
    @Query(
        "SELECT e.* FROM podcast_episode e JOIN podcast_feed f ON f.id = e.feed_id " +
            "WHERE e.played = 0 AND e.added_at > f.subscribed_at ORDER BY e.published_at DESC LIMIT :limit",
    )
    abstract fun newEpisodeList(limit: Int): List<PodcastEpisode>

    /** 1.0: every episode's title, the newest first: what Android Auto's search and voice requests compare. */
    @Query("SELECT id, feed_id, title, published_at FROM podcast_episode ORDER BY published_at DESC")
    abstract fun episodeTitles(): List<PodcastEpisodeTitle>

    @Query("SELECT * FROM podcast_episode WHERE id IN (:ids)")
    abstract fun episodesById(ids: List<Long>): List<PodcastEpisode>

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
                    volumeBoost = now.volumeBoost,
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
