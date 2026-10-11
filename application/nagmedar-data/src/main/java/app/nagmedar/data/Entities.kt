package app.nagmedar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A podcast subscription (Podcast Index or a feed address). */
@Entity(tableName = "podcast_feed", indices = [Index(value = ["feed_url"], unique = true)])
data class PodcastFeed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feed_url") val feedUrl: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "podcast_index_id") val podcastIndexId: Long? = null,
    /** HTTP validators for conditional refresh. */
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: Long = 0,
    @ColumnInfo(name = "auto_download") val autoDownload: Boolean = false,
    @ColumnInfo(name = "subscribed_at") val subscribedAt: Long = 0,
)

object DownloadState {
    const val NONE = 0
    const val QUEUED = 1
    const val RUNNING = 2
    const val DONE = 3
    const val FAILED = 4
}

@Entity(
    tableName = "podcast_episode",
    foreignKeys = [ForeignKey(entity = PodcastFeed::class, parentColumns = ["id"], childColumns = ["feed_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["feed_id", "guid"], unique = true), Index(value = ["published_at"])],
)
data class PodcastEpisode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feed_id") val feedId: Long,
    val guid: String,
    val title: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "published_at") val publishedAt: Long = 0,
    val description: String? = null,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    @ColumnInfo(name = "download_path") val downloadPath: String? = null,
    @ColumnInfo(name = "download_state") val downloadState: Int = DownloadState.NONE,
)

/** An internet radio station: from the Radio Browser directory (with its UUID) or added by hand. */
@Entity(tableName = "radio_station", indices = [Index(value = ["station_uuid"], unique = true)])
data class RadioStation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "station_uuid") val stationUuid: String? = null,
    val name: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    val homepage: String? = null,
    @ColumnInfo(name = "favicon_url") val faviconUrl: String? = null,
    @ColumnInfo(name = "country_code") val countryCode: String? = null,
    val tags: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val favorite: Boolean = false,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = 0,
)

/**
 * Cached lyrics of a track, one row per source (LRCLIB, embedded SYLT/USLT, .lrc file, user edit).
 * [trackKey] is [LyricsKey.of] the track's tags and length.
 */
@Entity(tableName = "lyrics_cache", primaryKeys = ["track_key", "source"], indices = [Index(value = ["fetched_at"])])
data class LyricsEntry(
    @ColumnInfo(name = "track_key") val trackKey: String,
    val source: String,
    val synced: Boolean,
    val text: String,
    val language: String? = null,
    @ColumnInfo(name = "offset_ms") val offsetMs: Long = 0,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long = 0,
)

object LyricsSource {
    const val LRCLIB = "lrclib"
    const val EMBEDDED = "embedded"
    const val FILE = "file"
    const val USER = "user"
}

object LyricsKey {
    /** Same track, same key: lower-case tags without surrounding spaces, length rounded to seconds. */
    fun of(artist: String?, title: String?, album: String?, lengthMs: Long): String =
        listOf(artist, title, album).joinToString("|") { it?.trim()?.lowercase().orEmpty() } + "|" + (lengthMs / 1000)
}

/** Settings remembered per file: delays, tracks, aspect ratio, speed, deinterlacing, equaliser. */
@Entity(tableName = "media_settings")
data class MediaSettings(
    @PrimaryKey @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "audio_delay_ms") val audioDelayMs: Long? = null,
    @ColumnInfo(name = "subtitle_delay_ms") val subtitleDelayMs: Long? = null,
    @ColumnInfo(name = "audio_track") val audioTrack: String? = null,
    @ColumnInfo(name = "subtitle_track") val subtitleTrack: String? = null,
    @ColumnInfo(name = "secondary_subtitle_track") val secondarySubtitleTrack: String? = null,
    @ColumnInfo(name = "subtitle_encoding") val subtitleEncoding: String? = null,
    @ColumnInfo(name = "aspect_ratio") val aspectRatio: String? = null,
    @ColumnInfo(name = "playback_rate") val playbackRate: Float? = null,
    val deinterlace: String? = null,
    @ColumnInfo(name = "equalizer_preset") val equalizerPreset: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

/**
 * S-802: the media probe's JSON for one file, kept until the file changes (size or modification
 * time) or the probe's format does ([probeVersion]).
 */
@Entity(tableName = "media_tech")
data class MediaTechEntry(
    @PrimaryKey @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "probe_version") val probeVersion: Int,
    val json: String,
    @ColumnInfo(name = "probed_at") val probedAt: Long = 0,
)
