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
     * Whether the output is the job's own to remove or empty: set once the job found it empty
     * and began writing. An output refused (the source itself, or a file that held something)
     * is never touched.
     */
    @ColumnInfo(name = "owns_output") val ownsOutput: Boolean = false,
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
    /** Queued, but the system let no foreground job start from the background: it starts when the app shows. */
    const val WAITING_APP = "waiting-app"
    /** The engine's own message follows the prefix. */
    const val ENGINE = "engine:"
}
