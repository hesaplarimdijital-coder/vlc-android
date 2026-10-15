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

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 9

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

        /** Schema migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
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
