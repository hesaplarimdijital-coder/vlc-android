package app.nagmedar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A podcast subscription (Podcast Index or a feed address; S-1101). Room is the one place
 * subscriptions live: the medialibrary's own Subscription table is not mirrored (see
 * PodcastRepository in nagmedar-ui for why).
 */
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
    /** S-1101: the show's own web page. */
    val link: String? = null,
    /** S-1101: Podcasting 2.0's `podcast:guid`, the show's identity across hosts. */
    @ColumnInfo(name = "podcast_guid") val podcastGuid: String? = null,
    /** S-1101: Podcasting 2.0's `podcast:funding`: where to support the show, and its words. */
    @ColumnInfo(name = "funding_url") val fundingUrl: String? = null,
    @ColumnInfo(name = "funding_message") val fundingMessage: String? = null,
    /**
     * S-1101: the other addresses the feed was reached by (the one a search result or an OPML file gave, before a
     * permanent redirect moved it to [feedUrl]), as keys, one per line: a result is told as subscribed by any of them.
     */
    @ColumnInfo(name = "alias_keys", defaultValue = "''") val aliasKeys: String = "",
    /** S-1103: a notification when the refresh finds new episodes of the show. */
    @ColumnInfo(name = "notify", defaultValue = "1") val notify: Boolean = true,
    /** S-1103: with [autoDownload] on, how many of the new episodes of a refresh are downloaded by themselves, the newest first. */
    @ColumnInfo(name = "auto_download_count", defaultValue = "3") val autoDownloadCount: Int = 3,
    /** S-1103: how many downloaded episodes of the show are kept, the newest; 0 keeps them all. */
    @ColumnInfo(name = "keep_latest", defaultValue = "0") val keepLatest: Int = 0,
    /** S-1104: the speed the show's episodes play at, 0.5 to 3. */
    @ColumnInfo(name = "speed", defaultValue = "1.0") val speed: Float = 1f,
    /** S-1104: seconds skipped at the start of every episode (the show's jingle), 0 for none. */
    @ColumnInfo(name = "skip_intro_s", defaultValue = "0") val skipIntroSeconds: Int = 0,
    /** S-1104: seconds before the end from which an episode counts as played and the next one starts, 0 for none. */
    @ColumnInfo(name = "skip_outro_s", defaultValue = "0") val skipOutroSeconds: Int = 0,
    /** Version 16: the volume boost of the show's episodes: 0 off, 1 light, 2 strong. */
    @ColumnInfo(name = "volume_boost", defaultValue = "0") val volumeBoost: Int = 0,
    /** Version 21: the language the feed says the show is in (its `language`, as "tr" or "en-us"); null when it says none. */
    val language: String? = null,
    /**
     * Version 21: the language Whisper hears the show's episodes in when it writes their transcripts: a code ("tr",
     * "en"), "auto" for Whisper's own guess, null for the feed's [language] (Whisper's guess when it has none).
     */
    @ColumnInfo(name = "transcript_language") val transcriptLanguage: String? = null,
)

object DownloadState {
    const val NONE = 0
    const val QUEUED = 1
    const val RUNNING = 2
    const val DONE = 3
    const val FAILED = 4

    /** S-1103: a download taken away (by hand or by the storage limit): it is not downloaded again by itself. */
    const val REMOVED = 5
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
    /**
     * S-1101: when a refresh first saw the episode. Episodes added after the subscription and not
     * played are the feed's new ones; the first fetch's carry the subscription's own time.
     */
    @ColumnInfo(name = "added_at", defaultValue = "0") val addedAt: Long = 0,
    /** S-1101: the enclosure's length in bytes as the feed says; 0 when it does not. */
    @ColumnInfo(name = "size_bytes", defaultValue = "0") val sizeBytes: Long = 0,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    val season: Int? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    /** S-1101: Podcasting 2.0's `podcast:chapters` file and `podcast:transcript` file with its media type. */
    @ColumnInfo(name = "chapters_url") val chaptersUrl: String? = null,
    @ColumnInfo(name = "transcript_url") val transcriptUrl: String? = null,
    @ColumnInfo(name = "transcript_type") val transcriptType: String? = null,
    /** S-1103: the size of the downloaded file in bytes; 0 while there is none. */
    @ColumnInfo(name = "download_bytes", defaultValue = "0") val downloadBytes: Long = 0,
    /** S-1103: when the episode was marked played, in epoch milliseconds; 0 while it is not. */
    @ColumnInfo(name = "played_at", defaultValue = "0") val playedAt: Long = 0,
    /** S-1103: the show notes as the feed wrote them (HTML, cut at 64 KB), drawn by the episode's page after sanitising; null when the feed gave none. */
    @ColumnInfo(name = "notes_html") val notesHtml: String? = null,
    /**
     * Version 21: the Whisper model the app wrote the episode's own transcript with ("small"), kept in the app's storage
     * beside the podcasts' data; null while it has none.
     */
    @ColumnInfo(name = "whisper_model") val whisperModel: String? = null,
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
 * One track of a radio recording (S-1107): a file in the recordings' folder that a session cut
 * from a station's stream at a change of the stream's title. [sessionId] groups the tracks of one
 * recording; [stationKey] is the station's directory UUID, else its address, so a station is
 * grouped however it was reached. [trashedAt] is set while a deletion can still be taken back.
 */
@Entity(
    tableName = "radio_recording",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["file_uri"], unique = true),
        Index(value = ["session_started_at"]),
    ],
)
data class RadioRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "station_key") val stationKey: String,
    @ColumnInfo(name = "station_name") val stationName: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "station_uuid") val stationUuid: String? = null,
    @ColumnInfo(name = "logo_url") val logoUrl: String? = null,
    @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val artist: String? = null,
    val title: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0,
    /** Whether the recording saw both ends of the track: the first and last ones, and short ones kept, are partial. */
    val complete: Boolean = false,
    val format: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "session_started_at") val sessionStartedAt: Long,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long = 0,
)

/**
 * A scheduled radio recording (S-1107): a station, the wall-clock [hour]:[minute] it starts at, how
 * long it runs and how it repeats ([repeat] is the repeat's name; [weekday] is ISO, 1 Monday).
 * [startAt] is the first start, epoch ms; [lastStartedAt] the start last made (0: none).
 */
@Entity(tableName = "radio_schedule")
data class RadioScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "station_key") val stationKey: String,
    @ColumnInfo(name = "station_name") val stationName: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "station_uuid") val stationUuid: String? = null,
    @ColumnInfo(name = "logo_url") val logoUrl: String? = null,
    @ColumnInfo(name = "start_at") val startAt: Long,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val repeat: String,
    val weekday: Int = 1,
    val enabled: Boolean = true,
    @ColumnInfo(name = "last_started_at") val lastStartedAt: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    /** Until when the runs of this schedule were looked over for ones that were missed (ms; 0: not yet). */
    @ColumnInfo(name = "checked_at", defaultValue = "0") val checkedAt: Long = 0,
)

/**
 * One run of a scheduled radio recording (S-1107): the window [windowStart]..[windowEnd] (epoch ms)
 * of [scheduleId], and what came of it: [status] is the name of the run's state (Running, Done,
 * Stopped, Failed, Interrupted, Missed). [interruptions] counts the times a restart of the device
 * or of the app cut it off; [sessionId] is the recording that made it. One row per window.
 */
@Entity(
    tableName = "radio_schedule_run",
    indices = [
        Index(value = ["schedule_id", "window_start"], unique = true),
        Index(value = ["session_id"]),
    ],
)
data class RadioScheduleRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: Long,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    val status: String,
    val interruptions: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
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

    /** S-1005: what the file's tags held before the lyrics editor wrote to them; kept once, never overwritten. */
    const val ORIGINAL_TAG = "original_tag"

    /** S-1005: what the .lrc beside the file held before the lyrics editor wrote over it; kept once. */
    const val ORIGINAL_FILE = "original_file"
}

object LyricsKey {
    /** Same track, same key: lower-case tags without surrounding spaces, length rounded to seconds. */
    fun of(artist: String?, title: String?, album: String?, lengthMs: Long): String =
        listOf(artist, title, album).joinToString("|") { it?.trim()?.lowercase().orEmpty() } + "|" + (lengthMs / 1000)
}

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

/**
 * S-1301: a network server the user saved: its name, its address as libVLC reads it
 * ("smb://nas.local/Filmler"; never a user or a password in it) and its sign-in, sealed with an
 * Android Keystore key that never leaves the device (null: none kept). A system backup may carry
 * the row to another device, where the sign-in cannot be opened and is asked for again.
 */
@Entity(tableName = "network_server", indices = [Index(value = ["url"], unique = true)])
data class NetworkServerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    /** The user name and the password, sealed: the cipher's version, IV and text with its tag. */
    val credentials: ByteArray? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long = 0,
)

/**
 * S-1008: a scrobble waiting to be sent to [service] ("lastfm", "listenbrainz"): a track heard
 * for half its length or four minutes, with the time it started ([listenedAt], seconds since the
 * epoch). One row per service, so that each one's queue is sent, or stuck, on its own. The same
 * track at the same time is queued once ([service], [artist], [track], [listenedAt] are unique).
 */
@Entity(tableName = "scrobble_queue", indices = [Index(value = ["service", "artist", "track", "listened_at"], unique = true)])
data class ScrobbleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val service: String,
    val artist: String,
    val track: String,
    val album: String? = null,
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null,
    @ColumnInfo(name = "duration_sec") val durationSec: Int = 0,
    @ColumnInfo(name = "listened_at") val listenedAt: Long,
    /** How often sending it failed for a reason that may pass (no network, the server busy). */
    val attempts: Int = 0,
    @ColumnInfo(name = "queued_at") val queuedAt: Long = 0,
)

/** The scrobbles waiting for one service. */
data class ScrobbleCount(val service: String, val count: Int)
