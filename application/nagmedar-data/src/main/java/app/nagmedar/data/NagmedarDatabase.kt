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
    entities = [PodcastFeed::class, PodcastEpisode::class, RadioStation::class, LyricsEntry::class, MediaSettings::class, MediaTechEntry::class],
    version = NagmedarDatabase.VERSION,
    exportSchema = true,
)
abstract class NagmedarDatabase : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun radio(): RadioDao
    abstract fun lyrics(): LyricsDao
    abstract fun mediaSettings(): MediaSettingsDao
    abstract fun mediaTech(): MediaTechDao

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 2

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

        /** Schema migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
