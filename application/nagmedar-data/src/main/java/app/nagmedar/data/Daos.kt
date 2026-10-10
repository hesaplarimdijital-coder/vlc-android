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

@Dao
abstract class PodcastDao {
    @Query("SELECT * FROM podcast_feed ORDER BY title COLLATE NOCASE")
    abstract fun feeds(): Flow<List<PodcastFeed>>

    @Query("SELECT * FROM podcast_feed WHERE feed_url = :url")
    abstract suspend fun feedByUrl(url: String): PodcastFeed?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun subscribe(feed: PodcastFeed): Long

    @Update
    abstract suspend fun updateFeed(feed: PodcastFeed)

    /** Removes the feed and, through the foreign key, its episodes. */
    @Delete
    abstract suspend fun unsubscribe(feed: PodcastFeed)

    @Query("SELECT * FROM podcast_episode WHERE feed_id = :feedId ORDER BY published_at DESC")
    abstract fun episodes(feedId: Long): Flow<List<PodcastEpisode>>

    @Query("SELECT * FROM podcast_episode WHERE feed_id = :feedId ORDER BY published_at DESC")
    abstract suspend fun episodeList(feedId: Long): List<PodcastEpisode>

    @Query("SELECT * FROM podcast_episode WHERE played = 0 ORDER BY published_at DESC LIMIT :limit")
    abstract fun newEpisodes(limit: Int): Flow<List<PodcastEpisode>>

    @Query("SELECT COUNT(*) FROM podcast_episode WHERE feed_id = :feedId AND played = 0")
    abstract suspend fun unplayedCount(feedId: Long): Int

    /** New episodes are added, known ones (same feed and guid) keep their playback state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addEpisodes(episodes: List<PodcastEpisode>): List<Long>

    @Query("UPDATE podcast_episode SET position_ms = :positionMs, played = :played WHERE id = :episodeId")
    abstract suspend fun savePosition(episodeId: Long, positionMs: Long, played: Boolean)

    @Query("UPDATE podcast_episode SET download_state = :state, download_path = :path WHERE id = :episodeId")
    abstract suspend fun setDownload(episodeId: Long, state: Int, path: String?)

    @Transaction
    open suspend fun refreshFeed(feed: PodcastFeed, episodes: List<PodcastEpisode>) {
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
    suspend fun byUuid(uuid: String): RadioStation?

    @Upsert
    suspend fun save(station: RadioStation): Long

    @Query("UPDATE radio_station SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE radio_station SET last_played_at = :at WHERE id = :id")
    suspend fun markPlayed(id: Long, at: Long)

    @Delete
    suspend fun delete(station: RadioStation)
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE track_key = :trackKey ORDER BY synced DESC, fetched_at DESC")
    suspend fun forTrack(trackKey: String): List<LyricsEntry>

    @Upsert
    suspend fun save(entry: LyricsEntry)

    @Query("UPDATE lyrics_cache SET offset_ms = :offsetMs WHERE track_key = :trackKey AND source = :source")
    suspend fun setOffset(trackKey: String, source: String, offsetMs: Long)

    /** Drops downloaded lyrics older than [before]; user edits and local files stay. */
    @Query("DELETE FROM lyrics_cache WHERE fetched_at < :before AND source = 'lrclib'")
    suspend fun pruneDownloaded(before: Long): Int
}

@Dao
interface MediaSettingsDao {
    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    suspend fun get(uri: String): MediaSettings?

    @Query("SELECT * FROM media_settings WHERE media_uri = :uri")
    fun observe(uri: String): Flow<MediaSettings?>

    @Upsert
    suspend fun save(settings: MediaSettings)

    @Query("DELETE FROM media_settings WHERE media_uri = :uri")
    suspend fun forget(uri: String)
}
