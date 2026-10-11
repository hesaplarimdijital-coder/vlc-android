package app.nagmedar.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S-809: the Nağmedar database behaves as the features need it, and its schema history is tested. */
@RunWith(AndroidJUnit4::class)
class NagmedarDatabaseTest {

    private lateinit var db: NagmedarDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NagmedarDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun podcastEpisodesFollowTheirFeed() {
        val podcasts = db.podcasts()
        val feedId = podcasts.subscribe(PodcastFeed(feedUrl = "https://example.org/feed.xml", title = "Örnek Podcast"))
        val feed = podcasts.feedByUrl("https://example.org/feed.xml")!!
        assertEquals(feedId, feed.id)

        podcasts.refreshFeed(feed, listOf(
            PodcastEpisode(feedId = 0, guid = "a", title = "Bölüm 1", audioUrl = "https://example.org/1.mp3", publishedAt = 1),
            PodcastEpisode(feedId = 0, guid = "b", title = "Bölüm 2", audioUrl = "https://example.org/2.mp3", publishedAt = 2),
        ))
        val episodes = podcasts.episodeList(feedId)
        assertEquals(listOf("Bölüm 2", "Bölüm 1"), episodes.map { it.title })
        assertEquals(2, podcasts.unplayedCount(feedId))

        // playback state survives a refresh that lists the same episode again
        podcasts.savePosition(episodes.last().id, positionMs = 90_000, played = true)
        podcasts.refreshFeed(feed, listOf(PodcastEpisode(feedId = 0, guid = "a", title = "Bölüm 1", audioUrl = "https://example.org/1.mp3")))
        val first = podcasts.episodeList(feedId).single { it.guid == "a" }
        assertEquals(90_000L, first.positionMs)
        assertTrue(first.played)
        assertEquals(1, podcasts.unplayedCount(feedId))

        // unsubscribing removes the episodes
        podcasts.unsubscribe(feed)
        assertTrue(podcasts.episodeList(feedId).isEmpty())
        assertTrue(runBlocking { podcasts.feeds().first() }.isEmpty())
    }

    @Test
    fun radioStationsAreSavedOncePerDirectoryEntry() {
        val radio = db.radio()
        val id = radio.save(RadioStation(stationUuid = "uuid-1", name = "Radyo", streamUrl = "https://radio.example/a"))
        radio.setFavorite(id, true)
        radio.markPlayed(id, at = 42)
        assertEquals(listOf("Radyo"), runBlocking { radio.favorites().first() }.map { it.name })
        assertEquals(42L, runBlocking { radio.recent(10).first() }.single().lastPlayedAt)
        assertNotNull(radio.byUuid("uuid-1"))
        // stations added by hand have no directory id; several of them may coexist
        radio.save(RadioStation(name = "Elle 1", streamUrl = "https://a"))
        radio.save(RadioStation(name = "Elle 2", streamUrl = "https://b"))
        assertNull(radio.byUuid("missing"))
    }

    @Test
    fun lyricsAreKeyedByTrackAndSource() {
        val lyrics = db.lyrics()
        val key = LyricsKey.of(" Ada Karan ", "Sabaha Kadar", "Gece Mavisi", 241_900)
        assertEquals("ada karan|sabaha kadar|gece mavisi|241", key)
        lyrics.save(LyricsEntry(key, LyricsSource.LRCLIB, synced = true, text = "[00:01.00] …", fetchedAt = 10))
        lyrics.save(LyricsEntry(key, LyricsSource.FILE, synced = false, text = "…", fetchedAt = 20))
        lyrics.setOffset(key, LyricsSource.LRCLIB, 350)
        val entries = lyrics.forTrack(key)
        assertEquals(LyricsSource.LRCLIB, entries.first().source)
        assertEquals(350L, entries.first().offsetMs)
        assertEquals(1, lyrics.pruneDownloaded(before = 15))
        assertEquals(listOf(LyricsSource.FILE), lyrics.forTrack(key).map { it.source })
    }

    @Test
    fun mediaProbesAreCachedPerFile() {
        val tech = db.mediaTech()
        val uri = "file:///storage/T7/Kuzey Işığı.mkv"
        tech.save(MediaTechEntry(uri, fileSize = 62_600_000_000, modifiedAt = 1_700_000_000_000, probeVersion = 1, json = "{}", probedAt = 5))
        tech.save(MediaTechEntry(uri, fileSize = 62_600_000_000, modifiedAt = 1_700_000_000_000, probeVersion = 1, json = "{\"version\":1}", probedAt = 6))
        assertEquals("{\"version\":1}", tech.get(uri)!!.json)
        assertEquals(listOf(uri), runBlocking { tech.all().first() }.map { it.mediaUri })
        tech.forget(uri)
        assertNull(tech.get(uri))
    }

    @Test
    fun mediaSettingsArePerFile() {
        val settings = db.mediaSettings()
        val uri = "file:///storage/T7/Kuzey Işığı.mkv"
        settings.save(MediaSettings(uri, audioDelayMs = -120, subtitleEncoding = "Windows-1254"))
        settings.save(MediaSettings(uri, audioDelayMs = -80, subtitleEncoding = "Windows-1254", playbackRate = 1.0f))
        val saved = settings.get(uri)!!
        assertEquals(-80L, saved.audioDelayMs)
        assertEquals("Windows-1254", saved.subtitleEncoding)
        settings.forget(uri)
        assertNull(settings.get(uri))
    }
}

/**
 * Schema history: the database created from every exported schema opens with the current code
 * after the registered migrations, and keeps its rows.
 */
@RunWith(AndroidJUnit4::class)
class NagmedarDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NagmedarDatabase::class.java)

    private fun schemaExported(version: Int): Boolean = runCatching {
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("${NagmedarDatabase::class.java.canonicalName}/$version.json").close()
    }.isSuccess

    @Test
    fun everyVersionMigratesToTheLatest() {
        assumeTrue("schema 1 not exported yet", schemaExported(1))
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO media_settings (media_uri, audio_delay_ms, updated_at) VALUES ('file:///film.mkv', -120, 1)")
        }
        // 1 -> 2 (S-802): the probe cache appears, the settings stay
        helper.runMigrationsAndValidate(TEST_DB, NagmedarDatabase.VERSION, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT audio_delay_ms FROM media_settings WHERE media_uri = 'file:///film.mkv'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(-120L, c.getLong(0))
            }
            db.execSQL(
                "INSERT INTO media_tech (media_uri, file_size, modified_at, probe_version, json, probed_at) " +
                    "VALUES ('file:///film.mkv', 1, 2, 1, '{}', 3)",
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
