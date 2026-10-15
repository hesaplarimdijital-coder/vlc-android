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

    @Query("UPDATE podcast_episode SET position_ms = :positionMs, played = :played WHERE id = :episodeId")
    abstract fun savePosition(episodeId: Long, positionMs: Long, played: Boolean)

    @Query("UPDATE podcast_episode SET played = :played WHERE id = :episodeId")
    abstract fun setPlayed(episodeId: Long, played: Boolean)

    @Query("UPDATE podcast_episode SET download_state = :state, download_path = :path WHERE id = :episodeId")
    abstract fun setDownload(episodeId: Long, state: Int, path: String?)

    /** S-1101: a new subscription with the episodes its first fetch read; the feed's id. */
    @Transaction
    open fun subscribeWith(feed: PodcastFeed, episodes: List<PodcastEpisode>): Long {
        val id = subscribe(feed)
        addEpisodes(episodes.map { it.copy(feedId = id) })
        return id
    }

    @Transaction
    open fun refreshFeed(feed: PodcastFeed, episodes: List<PodcastEpisode>) {
        updateFeed(feed)
        addEpisodes(episodes.map { it.copy(feedId = feed.id) })
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
