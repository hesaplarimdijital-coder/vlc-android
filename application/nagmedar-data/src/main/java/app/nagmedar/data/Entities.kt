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

/**
 * S-1303: a video conversion profile of the user's (the presets are the app's): the container,
 * the video (none: [videoCodec] null) with its bit rate, largest height and frame rate (0: the
 * source's), the audio (none: [audioCodec] null) with its bit rate, channels and sample rate (0:
 * the source's), what happens to the subtitles. Codecs, the container and the subtitles' mode by
 * the names of the app's enums.
 */
@Entity(tableName = "transcode_profile", indices = [Index(value = ["name"], unique = true)])
data class TranscodeProfileEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val container: String,
    @ColumnInfo(name = "video_codec") val videoCodec: String? = null,
    @ColumnInfo(name = "video_kbps") val videoKbps: Int = 0,
    @ColumnInfo(name = "max_height") val maxHeight: Int = 0,
    val fps: Int = 0,
    @ColumnInfo(name = "audio_codec") val audioCodec: String? = null,
    @ColumnInfo(name = "audio_kbps") val audioKbps: Int = 0,
    val channels: Int = 2,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int = 0,
    val subtitles: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

/**
 * S-1303: a video conversion waiting, under way or done: the source and the new document the
 * system's picker made, the profile as JSON, how far it got, what came of it. One job runs at a
 * time ([TranscodeJobState]); a job left running by a process that died starts again from the
 * beginning. A job only ever writes into an output it found empty ([ownsOutput]), and only such
 * an output is ever emptied or removed. While paused or failed, [error] says why
 * ([TranscodeJobError]).
 */
@Entity(tableName = "transcode_job")
data class TranscodeJobEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "input_uri") val inputUri: String,
    @ColumnInfo(name = "input_name") val inputName: String,
    @ColumnInfo(name = "input_size") val inputSize: Long = 0,
    @ColumnInfo(name = "output_uri") val outputUri: String,
    @ColumnInfo(name = "output_name") val outputName: String,
    val profile: String,
    val state: String = TranscodeJobState.QUEUED,
    val progress: Float = 0f,
    @ColumnInfo(name = "media_ms") val mediaMs: Long = 0,
    @ColumnInfo(name = "total_ms") val totalMs: Long = 0,
    @ColumnInfo(name = "bytes_out") val bytesOut: Long = 0,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    val error: String? = null,
    /** Whether the job was started again after an interruption (the process died, the system stopped it). */
    val restarted: Boolean = false,
    @ColumnInfo(name = "cancel_requested") val cancelRequested: Boolean = false,
    @ColumnInfo(name = "owns_output") val ownsOutput: Boolean = false,
)

/** S-1303: the states of a [TranscodeJobEntry]; done, failed and cancelled are final. */
object TranscodeJobState {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    val FINAL = setOf(DONE, FAILED, CANCELLED)
}

/** S-1303: why a [TranscodeJobEntry] waits, failed or stopped. */
object TranscodeJobError {
    const val CANCELLED = "cancelled"
    const val BATTERY = "battery"
    const val THERMAL = "thermal"
    const val SAME_FILE = "same_file"
    const val OUTPUT_NOT_EMPTY = "output_not_empty"
    const val OPEN_INPUT = "open_input"
    const val OPEN_OUTPUT = "open_output"

    /** The system stopped the job (a foreground job's time limit): it waits for the app. */
    const val TIMEOUT = "timeout"

    /** libVLC's own reason follows the prefix. */
    const val ENGINE = "engine:"
}

/**
 * S-1206: a job of the remux queue (the TV Lab's lossless remux): what to read, what to write,
 * the plan (app.nagmedar.lab.RemuxPlan as JSON), where it got and what came of it. One job
 * runs at a time; the states are [RemuxJobState]. The engine's stats and the verification
 * (S-1209) stay as the JSON the native side wrote, for the history (S-208) to read; [videoHash]
 * is the verified video's SHA-256, set when the output was read back and matched. [events]
 * records what happened on the way (a line each, "<millis> <kind> <detail>": a pause for the
 * battery or heat, a restart after the process died). A job that was running when the process
 * died is started again from the beginning: a Matroska file cannot be resumed halfway. A job
 * only ever writes into an empty output ([ownsOutput]).
 */
@Entity(tableName = "remux_job")
data class RemuxJobEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "input_uri") val inputUri: String,
    @ColumnInfo(name = "input_name") val inputName: String,
    @ColumnInfo(name = "input_size") val inputSize: Long = 0,
    @ColumnInfo(name = "output_uri") val outputUri: String,
    @ColumnInfo(name = "output_name") val outputName: String,
    val plan: String,
    val state: String = RemuxJobState.QUEUED,
    /** 0..1 of the remux, then of the verification. */
    val progress: Float = 0f,
    @ColumnInfo(name = "media_seconds") val mediaSeconds: Double = 0.0,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Double = 0.0,
    @ColumnInfo(name = "bytes_out") val bytesOut: Long = 0,
    /** How many times it was started; more than once after an interruption. */
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    val result: String? = null,
    val verification: String? = null,
    @ColumnInfo(name = "video_hash") val videoHash: String? = null,
    /** Why it failed or paused: a [RemuxJobError] code, or the engine's message after "engine:". */
    val error: String? = null,
    val events: String = "",
    @ColumnInfo(name = "cancel_requested") val cancelRequested: Boolean = false,
    /**
     * Whether the output is the job's own to remove or empty: set when the job is queued into an
     * empty output (the document the picker or a batch has just made for it), or once it found it
     * empty and began writing; while no attempt has written into it, it is removed only while it
     * is still empty. An output refused (the source itself, or a file that held something) is
     * never touched. A failed or cancelled job that still owns its output could not remove it
     * yet: the queue tries again ([RemuxJobDao.leftovers]); once removed it is no longer owned.
     */
    @ColumnInfo(name = "owns_output") val ownsOutput: Boolean = false,
    /**
     * S-1210: where the job stands in the queue; the worker takes the waiting job with the
     * lowest, the queue's screen reorders them. A new job goes last ([RemuxJobDao.nextPosition]).
     */
    @ColumnInfo(defaultValue = "0") val position: Long = 0,
)

/** The states of a [RemuxJobEntry]; done, failed and cancelled are final. */
object RemuxJobState {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val VERIFYING = "verifying"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    val FINAL = setOf(DONE, FAILED, CANCELLED)
}

/** Why a [RemuxJobEntry] failed or waits, as its [RemuxJobEntry.error]. */
object RemuxJobError {
    /** The verification found the output's video different from the source's. */
    const val VERIFY = "verify"
    const val CANCELLED = "cancelled"
    const val NO_TOOLS = "no-tools"
    const val OPEN_INPUT = "open-input"
    const val OPEN_OUTPUT = "open-output"
    const val BATTERY = "battery"
    const val THERMAL = "thermal"
    /** The output names the source itself: refused before anything is written. */
    const val SAME_FILE = "same-file"
    /** The output already held something (a film "replaced" in the picker): refused, untouched. */
    const val OUTPUT_NOT_EMPTY = "output-not-empty"
    /** The output is not a regular file (a pipe, a socket): whether it is empty cannot be known; refused, untouched. */
    const val OUTPUT_NOT_FILE = "output-not-file"
    /** Another job writes into the same output, or it is another job's file: refused, untouched. */
    const val OUTPUT_IN_USE = "output-in-use"
    /** Queued, but the system let no foreground job start from the background: it starts when the app shows. */
    const val WAITING_APP = "waiting-app"
    /** S-1210: paused with the whole queue by the user; it goes on when the queue is resumed. */
    const val HELD = "held"
    /** The engine's own message follows the prefix. */
    const val ENGINE = "engine:"
}

/**
 * Films and series (film information): what TMDB says of one film or series of the library, and
 * the IMDb rating and Rotten Tomatoes score OMDb gives for its IMDb id. [queryKey] names what was
 * looked for: "m:<title>:<year>" for a film, "tv:<name>" for a series, from the file names alone.
 * A [tmdbId] of 0 is a search that found nothing, tried again later. [manual] is a match the user
 * chose (the "wrong film? search" dialog), which a refresh keeps. [cast] holds "name\tcharacter" lines,
 * [genres] the genre names joined by "|"; [language] is the language of the texts ("tr", "en").
 */
@Entity(tableName = "film_info")
data class FilmInfoEntry(
    @PrimaryKey @ColumnInfo(name = "query_key") val queryKey: String,
    val kind: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Long,
    @ColumnInfo(name = "imdb_id") val imdbId: String? = null,
    val title: String,
    @ColumnInfo(name = "original_title") val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val language: String,
    val genres: String? = null,
    @ColumnInfo(name = "runtime_minutes") val runtimeMinutes: Int? = null,
    @ColumnInfo(name = "tmdb_rating") val tmdbRating: Double? = null,
    @ColumnInfo(name = "tmdb_votes") val tmdbVotes: Int? = null,
    @ColumnInfo(name = "imdb_rating") val imdbRating: Double? = null,
    @ColumnInfo(name = "imdb_votes") val imdbVotes: Int? = null,
    @ColumnInfo(name = "rotten_tomatoes") val rottenTomatoes: Int? = null,
    @ColumnInfo(name = "poster_path") val posterPath: String? = null,
    @ColumnInfo(name = "backdrop_path") val backdropPath: String? = null,
    val cast: String? = null,
    @ColumnInfo(name = "season_count") val seasonCount: Int? = null,
    @ColumnInfo(name = "episode_count") val episodeCount: Int? = null,
    val manual: Boolean = false,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** Film information: the kinds of a [FilmInfoEntry]. */
object FilmKind {
    const val MOVIE = "movie"
    const val TV = "tv"
}

/**
 * Film information: one episode of a series as TMDB names it, by the series' TMDB id. A season TMDB
 * has no episodes of is kept as one row numbered [NO_EPISODES], so that it is not asked for again
 * on every visit.
 */
@Entity(tableName = "film_episode", primaryKeys = ["show_id", "season", "episode"])
data class FilmEpisodeEntry(
    @ColumnInfo(name = "show_id") val showId: Long,
    val season: Int,
    val episode: Int,
    val title: String? = null,
    val overview: String? = null,
    @ColumnInfo(name = "still_path") val stillPath: String? = null,
    @ColumnInfo(name = "air_date") val airDate: String? = null,
    @ColumnInfo(name = "runtime_minutes") val runtimeMinutes: Int? = null,
    val language: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
) {
    companion object {
        /** The episode number of a season's row that says TMDB had no episodes of it when it was asked ([fetchedAt]). */
        const val NO_EPISODES = -1
    }
}
