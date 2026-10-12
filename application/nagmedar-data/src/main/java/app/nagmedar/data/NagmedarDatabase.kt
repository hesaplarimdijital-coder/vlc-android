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
        AudioTechEntry::class,
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

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 5

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

        /** Schema migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

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
