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

/**
 * An internet radio station: from the Radio Browser directory (with its UUID) or imported from a
 * playlist file (no UUID). A row exists while the station is a favourite, was played or was
 * imported (S-1105, S-1106).
 */
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
    /** The country's name as the directory gives it. */
    val country: String? = null,
    /** The language's name as the directory gives it. */
    val language: String? = null,
    /** The directory's vote count when the station was last read. */
    val votes: Int = 0,
    /** When the user last voted for the station (0: never): a vote is cast once a day. */
    @ColumnInfo(name = "last_voted_at") val lastVotedAt: Long = 0,
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

/**
 * Settings remembered per file: delays, tracks, aspect ratio, speed, deinterlacing, equaliser,
 * (S-603) the rest of the effects panel's values, as the panel's JSON in [effects], and (S-604)
 * the subtitles' speed. A null column is not remembered: the player's own setting applies.
 */
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
    /** S-603: colour, crop, rotation, HDR, scaler, debanding and the audio effects, as JSON. */
    val effects: String? = null,
    /** S-604: the subtitles' speed, as the frame rates they were timed for and play at ("25>23.976"). */
    @ColumnInfo(name = "subtitle_rate") val subtitleRate: String? = null,
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

/**
 * S-803: a music file as the engine's probe read it (codec, rate, depth, channels, DSD, bitrate,
 * the composer), kept until the file changes (size or modification time) or the probe's format
 * does ([probeVersion]). The library's filters and smart lists read it; the medialibrary keeps
 * the rest of the tags.
 */
@Entity(tableName = "audio_tech")
data class AudioTechEntry(
    @PrimaryKey @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "probe_version") val probeVersion: Int,
    val codec: String,
    val container: String,
    val rate: Int,
    val channels: Int,
    val bits: Int,
    @ColumnInfo(name = "is_float") val isFloat: Boolean,
    @ColumnInfo(name = "dsd_rate") val dsdRate: Int,
    val bitrate: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val composer: String?,
    @ColumnInfo(name = "probed_at") val probedAt: Long = 0,
)

/**
 * S-808: what was done with a track: the rating given (0 none, 1–5 stars), the plays counted (half
 * of it heard, or four minutes), the skips (left for another track before that) and when it was
 * first and last played. Keyed by the track's uri (a CUE sheet's track: the image's uri and its
 * number, "…/album.flac#3"), so that a rescan of the medialibrary keeps them.
 */
@Entity(tableName = "track_stats")
data class TrackStatsEntry(
    @PrimaryKey @ColumnInfo(name = "track_key") val trackKey: String,
    val rating: Int = 0,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "skip_count") val skipCount: Int = 0,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Long = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

/**
 * S-805: a smart list: its name, its rules as text (a line each, "field|op|value"), whether all
 * of them or any must hold, its order ("column:true", null for the library's) and how many
 * tracks it takes at most (null: all).
 */
@Entity(tableName = "smart_playlist")
data class SmartPlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rules: String,
    @ColumnInfo(name = "match_all") val matchAll: Boolean = true,
    val sort: String? = null,
    @ColumnInfo(name = "limit_count") val limitCount: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

/**
 * S-613: an output device's latency profile: how much later than the app's clock it plays, ms,
 * set by hand or measured with the microphone. The device is AudioDeviceInfo's type and
 * product name, with its address where that is stable (Bluetooth); a device seen but not
 * set has a row at 0 so the list shows it.
 */
@Entity(tableName = "latency_profile", primaryKeys = ["device_type", "device_name", "device_address"])
data class LatencyProfileEntry(
    @ColumnInfo(name = "device_type") val deviceType: Int,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "device_address") val deviceAddress: String = "",
    @ColumnInfo(name = "delay_ms") val delayMs: Int = 0,
    /** The delay came from a measurement, with its spread (ms). */
    val measured: Boolean = false,
    @ColumnInfo(name = "spread_ms") val spreadMs: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    /** The built-in speaker's own round trip as measured, ms (the microphone's path); not applied, taken off other devices' measurements. */
    @ColumnInfo(name = "calibration_ms") val calibrationMs: Int? = null,
)
