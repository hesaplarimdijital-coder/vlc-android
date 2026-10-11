package app.nagmedar.data

import androidx.room.Dao
import androidx.room.Delete
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

@Dao
abstract class PodcastDao {
    @Query("SELECT * FROM podcast_feed ORDER BY title COLLATE NOCASE")
    abstract fun feeds(): Flow<List<PodcastFeed>>

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

    @Query("UPDATE podcast_episode SET download_state = :state, download_path = :path WHERE id = :episodeId")
    abstract fun setDownload(episodeId: Long, state: Int, path: String?)

    @Transaction
    open fun refreshFeed(feed: PodcastFeed, episodes: List<PodcastEpisode>) {
        updateFeed(feed)
        addEpisodes(episodes.map { it.copy(feedId = feed.id) })
    }
}

@Dao
interface RadioDao {
    @Query("SELECT * FROM radio_station WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_station WHERE last_played_at > 0 ORDER BY last_played_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_station WHERE station_uuid = :uuid")
    fun byUuid(uuid: String): RadioStation?

    @Upsert
    fun save(station: RadioStation): Long

    @Query("UPDATE radio_station SET favorite = :favorite WHERE id = :id")
    fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE radio_station SET last_played_at = :at WHERE id = :id")
    fun markPlayed(id: Long, at: Long)

    @Delete
    fun delete(station: RadioStation)
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
}

@Dao
interface MediaSettingsDao {
    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    fun get(uri: String): MediaSettings?

    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    fun observe(uri: String): Flow<MediaSettings?>

    @Upsert
    fun save(settings: MediaSettings)

    @Query("DELETE FROM media_settings WHERE media_uri = :uri")
    fun forget(uri: String)
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
