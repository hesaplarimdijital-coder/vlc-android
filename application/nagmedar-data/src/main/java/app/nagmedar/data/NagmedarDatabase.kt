package app.nagmedar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Nağmedar's Room database (S-809), separate from VLC's media database so upstream migrations
 * never collide with ours. Every schema version is exported to schemas/ and every migration
 * has a test (NagmedarDatabaseMigrationTest). The file lives in databases/, which the app's
 * backup rules include.
 */
@Database(
    entities = [
        PodcastFeed::class, PodcastEpisode::class, RadioStation::class, LyricsEntry::class, MediaSettings::class, MediaTechEntry::class,
        AudioTechEntry::class, TrackStatsEntry::class, SmartPlaylistEntry::class, LatencyProfileEntry::class,
        NetworkServerEntry::class, ScrobbleEntry::class, TranscodeProfileEntry::class, TranscodeJobEntry::class,
        RadioRecording::class, RadioScheduleEntry::class, RemuxJobEntry::class, RadioScheduleRun::class, FilmInfoEntry::class, FilmEpisodeEntry::class,
    ],
    version = NagmedarDatabase.VERSION,
    exportSchema = true,
)
abstract class NagmedarDatabase : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun radio(): RadioDao
    abstract fun lyrics(): LyricsDao
    abstract fun mediaSettings(): MediaSettingsDao
    abstract fun mediaTech(): MediaTechDao
    abstract fun audioTech(): AudioTechDao
    abstract fun trackStats(): TrackStatsDao
    abstract fun smartPlaylists(): SmartPlaylistDao
    abstract fun latencyProfiles(): LatencyProfileDao
    abstract fun networkServers(): NetworkServerDao
    abstract fun scrobbles(): ScrobbleDao
    abstract fun transcodeProfiles(): TranscodeProfileDao
    abstract fun transcodeJobs(): TranscodeJobDao
    abstract fun radioRecordings(): RadioRecordingDao
    abstract fun remuxJobs(): RemuxJobDao
    abstract fun filmInfo(): FilmInfoDao

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 19

        /** 1 → 2 (S-802): the media probe cache. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_tech` (`media_uri` TEXT NOT NULL, `file_size` INTEGER NOT NULL, " +
                        "`modified_at` INTEGER NOT NULL, `probe_version` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                        "`probed_at` INTEGER NOT NULL, PRIMARY KEY(`media_uri`))",
                )
            }
        }

        /** 2 → 3 (S-603): the effects panel's values per file. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_settings` ADD COLUMN `effects` TEXT")
            }
        }

        /** 3 → 4 (S-604): the subtitles' speed per file. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_settings` ADD COLUMN `subtitle_rate` TEXT")
            }
        }

        /** 4 → 5 (S-803): the music files' probe, for the library's filters. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_tech` (`media_uri` TEXT NOT NULL, `file_size` INTEGER NOT NULL, " +
                        "`modified_at` INTEGER NOT NULL, `probe_version` INTEGER NOT NULL, `codec` TEXT NOT NULL, " +
                        "`container` TEXT NOT NULL, `rate` INTEGER NOT NULL, `channels` INTEGER NOT NULL, `bits` INTEGER NOT NULL, " +
                        "`is_float` INTEGER NOT NULL, `dsd_rate` INTEGER NOT NULL, `bitrate` INTEGER NOT NULL, " +
                        "`duration_ms` INTEGER NOT NULL, `composer` TEXT, `probed_at` INTEGER NOT NULL, PRIMARY KEY(`media_uri`))",
                )
            }
        }

        /** 5 → 6 (S-808): ratings and play counts. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_stats` (`track_key` TEXT NOT NULL, `rating` INTEGER NOT NULL, " +
                        "`play_count` INTEGER NOT NULL, `skip_count` INTEGER NOT NULL, `first_played_at` INTEGER NOT NULL, " +
                        "`last_played_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`track_key`))",
                )
            }
        }

        /** 6 → 7 (S-805): the smart lists. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `smart_playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`rules` TEXT NOT NULL, `match_all` INTEGER NOT NULL, `sort` TEXT, `limit_count` INTEGER, " +
                        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
            }
        }

        /** 7 → 8 (S-1105): the radio stations' country, language and votes, and when the user voted. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `radio_station` ADD COLUMN `country` TEXT")
                db.execSQL("ALTER TABLE `radio_station` ADD COLUMN `language` TEXT")
                db.execSQL("ALTER TABLE `radio_station` ADD COLUMN `votes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `radio_station` ADD COLUMN `last_voted_at` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 8 → 9 (S-613): the output devices' latency profiles. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `latency_profile` (`device_type` INTEGER NOT NULL, `device_name` TEXT NOT NULL, " +
                        "`device_address` TEXT NOT NULL, `delay_ms` INTEGER NOT NULL, `measured` INTEGER NOT NULL, `spread_ms` INTEGER, " +
                        "`updated_at` INTEGER NOT NULL, `calibration_ms` INTEGER, PRIMARY KEY(`device_type`, `device_name`, `device_address`))",
                )
            }
        }

        /** 9 → 10 (S-1301): the saved network servers. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `network_server` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `credentials` BLOB, `created_at` INTEGER NOT NULL, `last_used_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_network_server_url` ON `network_server` (`url`)")
            }
        }

        /** 10 → 11 (S-1101): what a feed's first fetch reads beyond the title, and the Podcasting 2.0 files. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("link TEXT", "podcast_guid TEXT", "funding_url TEXT", "funding_message TEXT", "alias_keys TEXT NOT NULL DEFAULT ''").forEach {
                    db.execSQL("ALTER TABLE `podcast_feed` ADD COLUMN $it")
                }
                listOf(
                    "added_at INTEGER NOT NULL DEFAULT 0", "size_bytes INTEGER NOT NULL DEFAULT 0", "image_url TEXT", "season INTEGER",
                    "episode_number INTEGER", "chapters_url TEXT", "transcript_url TEXT", "transcript_type TEXT",
                ).forEach { db.execSQL("ALTER TABLE `podcast_episode` ADD COLUMN $it") }
            }
        }

        /** 11 → 12 (S-1103, S-1104): the shows' notifications, downloads and playback settings, and the episodes' downloads, played time and show notes. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "notify INTEGER NOT NULL DEFAULT 1", "auto_download_count INTEGER NOT NULL DEFAULT 3", "keep_latest INTEGER NOT NULL DEFAULT 0",
                    "speed REAL NOT NULL DEFAULT 1.0", "skip_intro_s INTEGER NOT NULL DEFAULT 0", "skip_outro_s INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE `podcast_feed` ADD COLUMN $it") }
                listOf("download_bytes INTEGER NOT NULL DEFAULT 0", "played_at INTEGER NOT NULL DEFAULT 0", "notes_html TEXT")
                    .forEach { db.execSQL("ALTER TABLE `podcast_episode` ADD COLUMN $it") }
            }
        }

        /** 12 → 13 (S-1008): the scrobbles waiting to be sent. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrobble_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `service` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, `track` TEXT NOT NULL, `album` TEXT, `album_artist` TEXT, `duration_sec` INTEGER NOT NULL, " +
                        "`listened_at` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `queued_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_scrobble_queue_service_artist_track_listened_at` " +
                        "ON `scrobble_queue` (`service`, `artist`, `track`, `listened_at`)",
                )
            }
        }

        /** 13 → 14 (S-1303): the user's video conversion profiles and the conversions' queue. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcode_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`container` TEXT NOT NULL, `video_codec` TEXT, `video_kbps` INTEGER NOT NULL, `max_height` INTEGER NOT NULL, " +
                        "`fps` INTEGER NOT NULL, `audio_codec` TEXT, `audio_kbps` INTEGER NOT NULL, `channels` INTEGER NOT NULL, " +
                        "`sample_rate` INTEGER NOT NULL, `subtitles` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transcode_profile_name` ON `transcode_profile` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcode_job` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`input_uri` TEXT NOT NULL, `input_name` TEXT NOT NULL, `input_size` INTEGER NOT NULL, " +
                        "`output_uri` TEXT NOT NULL, `output_name` TEXT NOT NULL, `profile` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`progress` REAL NOT NULL, `media_ms` INTEGER NOT NULL, `total_ms` INTEGER NOT NULL, `bytes_out` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `started_at` INTEGER, `finished_at` INTEGER, " +
                        "`error` TEXT, `restarted` INTEGER NOT NULL, `cancel_requested` INTEGER NOT NULL, `owns_output` INTEGER NOT NULL)",
                )
            }
        }

        /** 14 → 15 (S-1107): the tracks radio recordings were cut into, and the recordings scheduled. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `radio_recording` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`session_id` TEXT NOT NULL, `station_key` TEXT NOT NULL, `station_name` TEXT NOT NULL, " +
                        "`stream_url` TEXT NOT NULL, `station_uuid` TEXT, `logo_url` TEXT, `file_uri` TEXT NOT NULL, " +
                        "`file_name` TEXT NOT NULL, `artist` TEXT, `title` TEXT, `duration_ms` INTEGER NOT NULL, " +
                        "`size_bytes` INTEGER NOT NULL, `complete` INTEGER NOT NULL, `format` TEXT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, `session_started_at` INTEGER NOT NULL, `trashed_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_recording_session_id` ON `radio_recording` (`session_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_radio_recording_file_uri` ON `radio_recording` (`file_uri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_recording_session_started_at` ON `radio_recording` (`session_started_at`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `radio_schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`station_key` TEXT NOT NULL, `station_name` TEXT NOT NULL, `stream_url` TEXT NOT NULL, " +
                        "`station_uuid` TEXT, `logo_url` TEXT, `start_at` INTEGER NOT NULL, `hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, `duration_minutes` INTEGER NOT NULL, `repeat` TEXT NOT NULL, " +
                        "`weekday` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `last_started_at` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)",
                )
            }
        }

        /** 15 → 16 (S-1206): the remux queue. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `remux_job` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`input_uri` TEXT NOT NULL, `input_name` TEXT NOT NULL, `input_size` INTEGER NOT NULL, " +
                        "`output_uri` TEXT NOT NULL, `output_name` TEXT NOT NULL, `plan` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`progress` REAL NOT NULL, `media_seconds` REAL NOT NULL, `total_seconds` REAL NOT NULL, " +
                        "`bytes_out` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                        "`started_at` INTEGER, `finished_at` INTEGER, `result` TEXT, `verification` TEXT, `video_hash` TEXT, " +
                        "`error` TEXT, `events` TEXT NOT NULL, `cancel_requested` INTEGER NOT NULL, `owns_output` INTEGER NOT NULL)",
                )
            }
        }

        /** 16 → 17 (S-1107): what came of each run of a scheduled recording, and when its runs were last looked over. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `radio_schedule` ADD COLUMN `checked_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `radio_schedule_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`schedule_id` INTEGER NOT NULL, `window_start` INTEGER NOT NULL, `window_end` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `interruptions` INTEGER NOT NULL, `session_id` TEXT, `updated_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_radio_schedule_run_schedule_id_window_start` ON `radio_schedule_run` (`schedule_id`, `window_start`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_schedule_run_session_id` ON `radio_schedule_run` (`session_id`)")
            }
        }

        /** 17 → 18 (S-1104 follow-up): the volume boost of a show's episodes. */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `podcast_feed` ADD COLUMN `volume_boost` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 18 → 19 (film information): the films' and series' TMDB details and the series' episodes. */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `film_info` (`query_key` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`tmdb_id` INTEGER NOT NULL, `imdb_id` TEXT, `title` TEXT NOT NULL, `original_title` TEXT, " +
                        "`year` INTEGER, `overview` TEXT, `language` TEXT NOT NULL, `genres` TEXT, `runtime_minutes` INTEGER, " +
                        "`tmdb_rating` REAL, `tmdb_votes` INTEGER, `imdb_rating` REAL, `imdb_votes` INTEGER, " +
                        "`rotten_tomatoes` INTEGER, `poster_path` TEXT, `backdrop_path` TEXT, `cast` TEXT, " +
                        "`season_count` INTEGER, `episode_count` INTEGER, `manual` INTEGER NOT NULL, " +
                        "`fetched_at` INTEGER NOT NULL, PRIMARY KEY(`query_key`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `film_episode` (`show_id` INTEGER NOT NULL, `season` INTEGER NOT NULL, " +
                        "`episode` INTEGER NOT NULL, `title` TEXT, `overview` TEXT, `still_path` TEXT, `air_date` TEXT, " +
                        "`runtime_minutes` INTEGER, `language` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`show_id`, `season`, `episode`))",
                )
            }
        }

        /** Schema migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        )

        @Volatile
        private var instance: NagmedarDatabase? = null

        fun get(context: Context): NagmedarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NagmedarDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
