package app.nagmedar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Nağmedar's Room database (S-809), separate from VLC's media database so upstream migrations
 * never collide with ours. Every schema version is exported to schemas/ and every migration
 * has a test (NagmedarDatabaseMigrationTest). The file lives in databases/, which the app's
 * backup rules include.
 */
@Database(
    entities = [
        PodcastFeed::class, PodcastEpisode::class, RadioStation::class, LyricsEntry::class, MediaTechEntry::class,
        AudioTechEntry::class, TrackStatsEntry::class, SmartPlaylistEntry::class, LatencyProfileEntry::class,
        NetworkServerEntry::class, ScrobbleEntry::class, RadioRecording::class, RadioScheduleEntry::class, RadioScheduleRun::class,
    ],
    version = NagmedarDatabase.VERSION,
    exportSchema = true,
)
abstract class NagmedarDatabase : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun radio(): RadioDao
    abstract fun lyrics(): LyricsDao
    abstract fun mediaTech(): MediaTechDao
    abstract fun audioTech(): AudioTechDao
    abstract fun trackStats(): TrackStatsDao
    abstract fun smartPlaylists(): SmartPlaylistDao
    abstract fun latencyProfiles(): LatencyProfileDao
    abstract fun networkServers(): NetworkServerDao
    abstract fun scrobbles(): ScrobbleDao
    abstract fun radioRecordings(): RadioRecordingDao

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 22

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

        /*
         * 15 → 16 … 19 → 20 take nothing for granted. Before their branches were renumbered, debug builds of pull
         * requests (which install over this one) gave some of these numbers other meanings: 16 was the radio's
         * schedule runs in one, the podcasts' volume boost in another, the film information in a third, and 17 the
         * remux queue's order. A database such a build left at 16 or 17 has steps of later numbers and lacks those of
         * its own, so each step adds only what the database lacks (PRAGMA table_info for a column, IF NOT EXISTS for a
         * table or an index), and 19 → 20 takes every step from 15 on again: whatever such a build made of it, the
         * database ends as 20 is. (Those builds made the very tables and columns of these steps.)
         */

        /** 15 → 16 (S-1206): the remux queue. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) = remuxQueue(db)
        }

        /** 16 → 17 (S-1107): what came of each run of a scheduled recording, and when its runs were last looked over. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) = scheduleRuns(db)
        }

        /** 17 → 18 (S-1104 follow-up): the volume boost of a show's episodes. */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) = volumeBoost(db)
        }

        /** 18 → 19 (film information): the films' and series' TMDB details and the series' episodes. */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) = filmInfo(db)
        }

        /** 19 → 20 (S-1210): the remux queue's order; and every step from 15 on again, for what a pull request's build left. */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                remuxQueue(db)
                scheduleRuns(db)
                volumeBoost(db)
                filmInfo(db)
                remuxOrder(db)
            }
        }

        /** 20 → 21 (podcast transcripts by Whisper): the show's language from its feed and for Whisper, the episode's own transcript. */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) = transcripts(db)
        }

        private fun transcripts(db: SupportSQLiteDatabase) {
            db.addColumn("podcast_feed", "language", "TEXT")
            db.addColumn("podcast_feed", "transcript_language", "TEXT")
            db.addColumn("podcast_episode", "whisper_model", "TEXT")
        }

        /**
         * 21 → 22 (Nağmedar 1.0, pure audio): the tables only the removed features used go, with their rows: the
         * video player's settings per file (media_settings), the video conversions (transcode_profile, transcode_job),
         * the TV Lab's remux queue (remux_job) and the film information (film_info, film_episode). IF EXISTS: a
         * database a pull request's build left without one of them (see 15 → 16 …) migrates all the same. The music,
         * podcast, radio, lyrics, statistics and other tables stay as they are.
         *
         * The files the jobs read and wrote kept the system's permission for their worker; before the jobs go, their
         * addresses are written to [grants] (one a line), for the app to let go of what nothing else holds
         * (app.nagmedar.ui.work.RetiredWork). That note is a courtesy: when it cannot be made, the tables go all the same.
         */
        private class Migration21To22(private val grants: (() -> File)?) : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                grants?.let { file -> runCatching { noteGrants(db, file()) } }
                RETIRED_TABLES.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
            }

            private fun noteGrants(db: SupportSQLiteDatabase, file: File) {
                val uris = sortedSetOf<String>()
                for (table in listOf("remux_job", "transcode_job")) {
                    val there = db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { it.moveToFirst() }
                    if (!there) continue
                    db.query("SELECT input_uri, output_uri FROM `$table`").use { c ->
                        while (c.moveToNext()) for (i in 0..1) c.getString(i)?.takeIf { it.isNotBlank() }?.let(uris::add)
                    }
                }
                if (uris.isEmpty()) return
                // added to a note an earlier, unfinished try left
                val earlier = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
                file.writeText((earlier + uris).distinct().joinToString("\n", postfix = "\n"))
            }
        }

        /** The tables 21 → 22 drops. */
        val RETIRED_TABLES = listOf("media_settings", "transcode_profile", "transcode_job", "remux_job", "film_info", "film_episode")

        /** Where 21 → 22 notes the files whose permissions the dropped jobs held (see [Migration21To22]). */
        fun retiredGrants(context: Context): File = File(context.noBackupFilesDir, RETIRED_GRANTS)

        private const val RETIRED_GRANTS = "nagmedar_retired_grants"

        private fun remuxQueue(db: SupportSQLiteDatabase) {
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

        private fun scheduleRuns(db: SupportSQLiteDatabase) {
            db.addColumn("radio_schedule", "checked_at", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `radio_schedule_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`schedule_id` INTEGER NOT NULL, `window_start` INTEGER NOT NULL, `window_end` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `interruptions` INTEGER NOT NULL, `session_id` TEXT, `updated_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_radio_schedule_run_schedule_id_window_start` ON `radio_schedule_run` (`schedule_id`, `window_start`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_schedule_run_session_id` ON `radio_schedule_run` (`session_id`)")
        }

        private fun volumeBoost(db: SupportSQLiteDatabase) {
            db.addColumn("podcast_feed", "volume_boost", "INTEGER NOT NULL DEFAULT 0")
        }

        private fun filmInfo(db: SupportSQLiteDatabase) {
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

        /** The queue's jobs keep the order they were queued in; a queue that had the order already (a build of 17) keeps its own. */
        private fun remuxOrder(db: SupportSQLiteDatabase) {
            if (db.addColumn("remux_job", "position", "INTEGER NOT NULL DEFAULT 0")) db.execSQL("UPDATE `remux_job` SET `position` = `id`")
        }

        /** ALTER TABLE … ADD COLUMN, unless [table] has [column] already; whether it was added. */
        private fun SupportSQLiteDatabase.addColumn(table: String, column: String, definition: String): Boolean {
            val there = query("PRAGMA table_info(`$table`)").use { c ->
                val name = c.getColumnIndexOrThrow("name")
                var found = false
                while (!found && c.moveToNext()) found = c.getString(name) == column
                found
            }
            if (!there) execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            return !there
        }

        /** Schema migrations, oldest first; 21 → 22 notes the dropped jobs' files in [grants] when it is given. */
        fun migrations(grants: (() -> File)? = null): Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, Migration21To22(grants),
        )

        /** Schema migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = migrations()

        @Volatile
        private var instance: NagmedarDatabase? = null

        fun get(context: Context): NagmedarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NagmedarDatabase::class.java, NAME)
                .addMigrations(*migrations { retiredGrants(context.applicationContext) })
                .build()
                .also { instance = it }
        }
    }
}
