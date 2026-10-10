package app.nagmedar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Nağmedar's Room database (S-809), separate from VLC's media database so upstream migrations
 * never collide with ours. Every schema version is exported to schemas/ and every migration
 * has a test (NagmedarDatabaseMigrationTest). The file lives in databases/, which the app's
 * backup rules include.
 */
@Database(
    entities = [PodcastFeed::class, PodcastEpisode::class, RadioStation::class, LyricsEntry::class, MediaSettings::class],
    version = NagmedarDatabase.VERSION,
    exportSchema = true,
)
abstract class NagmedarDatabase : RoomDatabase() {
    abstract fun podcasts(): PodcastDao
    abstract fun radio(): RadioDao
    abstract fun lyrics(): LyricsDao
    abstract fun mediaSettings(): MediaSettingsDao

    companion object {
        const val NAME = "nagmedar.db"
        const val VERSION = 1

        /** Schema migrations, oldest first. Version 1 is the first schema, so there are none yet. */
        val MIGRATIONS: Array<Migration> = emptyArray()

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
