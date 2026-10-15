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
    fun aDirectoryStationReadAgainKeepsTheUsersMarks() {
        val radio = db.radio()
        val id = radio.keep(RadioStation(stationUuid = "uuid-7", name = "Radyo Yedi", streamUrl = "https://radio.example/7", votes = 10))
        radio.setFavorite(id, true)
        radio.markPlayed(id, at = 99)
        radio.markVoted(id, at = 77, votes = 11)
        // the directory says something else now; the favourite, the play and the vote stay
        val again = radio.keep(RadioStation(stationUuid = "uuid-7", name = "Radyo Yedi FM", streamUrl = "https://radio.example/7b", votes = 12, country = "Türkiye"))
        assertEquals(id, again)
        val kept = radio.byUuid("uuid-7")!!
        assertEquals("Radyo Yedi FM", kept.name)
        assertEquals("https://radio.example/7b", kept.streamUrl)
        assertEquals(12, kept.votes)
        assertEquals("Türkiye", kept.country)
        assertTrue(kept.favorite)
        assertEquals(99L, kept.lastPlayedAt)
        assertEquals(77L, kept.lastVotedAt)
    }

    @Test
    fun importedStationsAreThoseWithoutADirectoryId() {
        val radio = db.radio()
        radio.save(RadioStation(name = "Zeytin FM", streamUrl = "https://zeytin.example/canli"))
        radio.save(RadioStation(name = "Ada Radyo", streamUrl = "https://ada.example/canli"))
        radio.keep(RadioStation(stationUuid = "uuid-8", name = "Dizinden", streamUrl = "https://dizin.example/8"))
        assertEquals(listOf("Ada Radyo", "Zeytin FM"), runBlocking { radio.imported().first() }.map { it.name })
        assertEquals("Zeytin FM", radio.importedByUrl("https://zeytin.example/canli")?.name)
        assertNull(radio.importedByUrl("https://dizin.example/8"))
    }

    @Test
    fun anImportedStationKeptAgainIsTheSameRow() {
        val radio = db.radio()
        val first = radio.keep(RadioStation(name = "Zeytin FM", streamUrl = "https://zeytin.example/canli", faviconUrl = "https://zeytin.example/logo.png"))
        radio.setFavorite(first, true)
        radio.markPlayed(first, at = 41)
        val again = radio.keep(RadioStation(name = "Zeytin FM (yeni)", streamUrl = "https://zeytin.example/canli"))
        assertEquals(first, again)
        val kept = runBlocking { radio.imported().first() }.single()
        assertEquals("Zeytin FM (yeni)", kept.name)
        assertTrue(kept.favorite)
        assertEquals(41L, kept.lastPlayedAt)
        // a row that has its id is updated in place
        assertEquals(first, radio.keep(kept.copy(name = "Zeytin FM 2")))
        assertEquals("Zeytin FM 2", radio.byId(first)?.name)
        assertEquals(1, runBlocking { radio.imported().first() }.size)
    }

    @Test
    fun aVoteIsRememberedThroughPruning() {
        val radio = db.radio()
        val voted = radio.keep(RadioStation(stationUuid = "uuid-11", name = "Oy verilen", streamUrl = "https://a/11"))
        radio.markVoted(voted, at = 1_000, votes = 5)
        radio.pruneUnused()
        assertEquals(1_000L, radio.byUuid("uuid-11")?.lastVotedAt)
    }

    @Test
    fun forgettingAPlayedStationAndPruningTheUnused() {
        val radio = db.radio()
        val played = radio.keep(RadioStation(stationUuid = "uuid-9", name = "Çalınan", streamUrl = "https://a/9"))
        val loved = radio.keep(RadioStation(stationUuid = "uuid-10", name = "Sevilen", streamUrl = "https://a/10"))
        radio.markPlayed(played, at = 5)
        radio.markPlayed(loved, at = 6)
        radio.setFavorite(loved, true)
        radio.forgetPlayed(played)
        radio.forgetPlayed(loved)
        assertTrue(runBlocking { radio.recent(10).first() }.isEmpty())
        radio.pruneUnused()
        // the favourite stays, the one nothing refers to goes
        assertNull(radio.byUuid("uuid-9"))
        assertEquals("Sevilen", radio.byUuid("uuid-10")?.name)
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
        // S-1003: what was found counts, a miss does not; an instrumental's word does
        lyrics.save(LyricsEntry("x|miss||200", LyricsSource.LRCLIB, synced = false, text = "", fetchedAt = 12))
        lyrics.save(LyricsEntry("x|instrumental||200", LyricsSource.LRCLIB, synced = false, text = "", language = "zxx", fetchedAt = 30))
        assertEquals(2, lyrics.countDownloaded())
        assertEquals(2, lyrics.pruneDownloaded(before = 15))
        assertEquals(listOf(LyricsSource.FILE), lyrics.forTrack(key).map { it.source })
        assertEquals(1, lyrics.countDownloaded())
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
    fun musicProbesAreCachedPerFile() {
        val tech = db.audioTech()
        val uri = "file:///storage/emulated/0/Music/Itrî/Neva Kâr.flac"
        fun entry(bits: Int, composer: String?) = AudioTechEntry(
            uri, fileSize = 31_200_000, modifiedAt = 1_700_000_000_000, probeVersion = 1, codec = "flac", container = "flac",
            rate = 96_000, channels = 2, bits = bits, isFloat = false, dsdRate = 0, bitrate = 2_600_000, durationMs = 412_000,
            composer = composer, probedAt = 5,
        )
        tech.save(listOf(entry(16, null)))
        tech.save(listOf(entry(24, "Buhûrîzâde Mustafa Itrî")))
        assertEquals(24, tech.get(uri)!!.bits)
        assertEquals("Buhûrîzâde Mustafa Itrî", tech.get(uri)!!.composer)
        assertEquals(listOf(uri), tech.all().map { it.mediaUri })
        tech.forget(uri)
        assertNull(tech.get(uri))
    }

    @Test
    fun playsSkipsAndRatingsAddUpPerTrack() {
        val stats = db.trackStats()
        val key = "file:///storage/emulated/0/Music/Itrî/Neva Kâr.flac"
        stats.played(key, at = 100)
        stats.played(key, at = 200)
        stats.skipped(key, at = 300)
        stats.rate(key, rating = 4, at = 400)
        stats.get(key)!!.let {
            assertEquals(2, it.playCount)
            assertEquals(1, it.skipCount)
            assertEquals(4, it.rating)
            assertEquals("the first play stays", 100L, it.firstPlayedAt)
            assertEquals("a skip is not a play", 200L, it.lastPlayedAt)
            assertEquals(400L, it.updatedAt)
        }
        // a rating of a track never played; out of range, the nearest star
        stats.rate("$key#3", rating = 9, at = 500)
        assertEquals(5, stats.get("$key#3")!!.rating)
        assertEquals(0, stats.get("$key#3")!!.playCount)
        stats.rate(key, rating = 0, at = 600)
        assertEquals("taken back", 0, stats.get(key)!!.rating)
        assertEquals(2, runBlocking { stats.observe().first() }.size)
    }

    @Test
    fun smartListsAreKeptInTheOrderTheyWereMade() {
        val smart = db.smartPlaylists()
        val first = smart.insert(SmartPlaylistEntry(name = "En sevilenler", rules = "rating|at_least|4", sort = "rating:false", createdAt = 1, updatedAt = 1))
        val second = smart.insert(SmartPlaylistEntry(name = "Hiç çalınmayanlar", rules = "last_played|never|", matchAll = false, limitCount = 50, createdAt = 2, updatedAt = 2))
        assertEquals(listOf("En sevilenler", "Hiç çalınmayanlar"), runBlocking { smart.observe().first() }.map { it.name })
        smart.update(smart.get(first)!!.copy(name = "Beşlikler", updatedAt = 3))
        assertEquals("Beşlikler", smart.get(first)!!.name)
        assertEquals(50, smart.get(second)!!.limitCount)
        smart.delete(first)
        assertEquals(listOf(second), runBlocking { smart.observe().first() }.map { it.id })
    }

    /** S-1301: a server once per address, by name; its sealed sign-in replaced or forgotten alone. */
    @Test
    fun networkServersAreKeptOncePerAddress() {
        val servers = db.networkServers()
        val nas = servers.insert(NetworkServerEntry(name = "NAS", url = "smb://nas.local/Filmler", credentials = byteArrayOf(1, 2, 3), createdAt = 1))
        val ftp = servers.insert(NetworkServerEntry(name = "arşiv", url = "ftp://192.168.1.20/", createdAt = 2))
        assertEquals(listOf("arşiv", "NAS"), runBlocking { servers.observe().first() }.map { it.name })
        val again = runCatching { servers.insert(NetworkServerEntry(name = "Yine NAS", url = "smb://nas.local/Filmler")) }
        assertTrue("one row per address", again.isFailure)
        assertEquals(nas, servers.byUrl("smb://nas.local/Filmler")!!.id)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(servers.get(nas)!!.credentials))
        servers.setCredentials(nas, byteArrayOf(9))
        servers.used(nas, at = 50)
        servers.get(nas)!!.let {
            assertTrue(byteArrayOf(9).contentEquals(it.credentials))
            assertEquals(50L, it.lastUsedAt)
            assertEquals("the rest stays", 1L, it.createdAt)
        }
        servers.update(servers.get(nas)!!.copy(name = "Salondaki NAS"))
        servers.setCredentials(nas, null)
        assertNull(servers.get(nas)!!.credentials)
        assertEquals("Salondaki NAS", servers.get(nas)!!.name)
        servers.delete(ftp)
        assertEquals(listOf(nas), servers.all().map { it.id })
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
        // S-603: the effects panel's values ride along, the delays stay
        settings.saveEffects(uri, "2.39:1", "auto", "rock", "{\"gain\":150}", updatedAt = 7)
        settings.get(uri)!!.let {
            assertEquals("{\"gain\":150}", it.effects)
            assertEquals("2.39:1", it.aspectRatio)
            assertEquals("rock", it.equalizerPreset)
            assertEquals(-80L, it.audioDelayMs)
        }
        settings.updateEffects(uri, null, null, null, null, updatedAt = 8)
        assertNull(settings.get(uri)!!.effects)
        assertEquals(-80L, settings.get(uri)!!.audioDelayMs)
        settings.forget(uri)
        assertNull(settings.get(uri))
        // a file without a row gets one
        settings.saveEffects("file:///yeni.mkv", "auto", "auto", null, "{}", updatedAt = 9)
        assertEquals("{}", settings.get("file:///yeni.mkv")!!.effects)
        // S-604: the delays and the subtitles' speed, the effects staying
        settings.saveSync("file:///yeni.mkv", 120, -350, "25>23.976", updatedAt = 10)
        settings.get("file:///yeni.mkv")!!.let {
            assertEquals(120L, it.audioDelayMs)
            assertEquals(-350L, it.subtitleDelayMs)
            assertEquals("25>23.976", it.subtitleRate)
            assertEquals("{}", it.effects)
        }
        settings.updateSync("file:///yeni.mkv", null, null, null, updatedAt = 11)
        assertNull(settings.get("file:///yeni.mkv")!!.audioDelayMs)
        assertEquals("auto", settings.get("file:///yeni.mkv")!!.aspectRatio)
    }

    /** S-1502: what a backup reads, and its rows restored over these without losing either's. */
    @Test
    fun aBackupsRowsAreReadAndRestored() {
        val stats = db.trackStats()
        stats.played("a", 100)
        stats.played("a", 200)
        stats.rate("b", 3, 50)
        stats.restore(
            listOf(
                TrackStatsEntry("a", rating = 4, playCount = 5, firstPlayedAt = 50, lastPlayedAt = 150, updatedAt = 150),
                TrackStatsEntry("c", playCount = 1, lastPlayedAt = 10, updatedAt = 10),
            ),
        )
        assertEquals(TrackStatsEntry("a", 4, 5, 0, 50, 200, 200), stats.get("a"))
        assertEquals(3, stats.get("b")!!.rating)
        assertEquals(1, stats.get("c")!!.playCount)
        assertEquals(listOf("a", "b", "c"), stats.all().map { it.trackKey })

        // the lyrics a backup keeps: the user's and those given a delay
        val lyrics = db.lyrics()
        lyrics.save(LyricsEntry("t1", LyricsSource.LRCLIB, synced = true, text = "[00:01.00]bir", fetchedAt = 1))
        lyrics.save(LyricsEntry("t2", LyricsSource.LRCLIB, synced = true, text = "[00:01.00]iki", offsetMs = 300, fetchedAt = 1))
        lyrics.save(LyricsEntry("t3", LyricsSource.USER, synced = false, text = "üç"))
        assertEquals(listOf("t2", "t3"), lyrics.kept().map { it.trackKey })
        lyrics.saveAll(listOf(LyricsEntry("t1", LyricsSource.LRCLIB, synced = true, text = "[00:01.00]bir", offsetMs = -200, fetchedAt = 1)))
        assertEquals(-200L, lyrics.forTrack("t1").single().offsetMs)

        // a file's settings: the newer row
        val files = db.mediaSettings()
        files.save(MediaSettings("u1", audioDelayMs = 50, updatedAt = 10))
        files.restore(listOf(MediaSettings("u1", audioDelayMs = -120, updatedAt = 5), MediaSettings("u2", subtitleDelayMs = 400, updatedAt = 1)))
        assertEquals(50L, files.get("u1")!!.audioDelayMs)
        assertEquals(400L, files.get("u2")!!.subtitleDelayMs)
        assertEquals(listOf("u1", "u2"), files.all().map { it.mediaUri })

        val smart = db.smartPlaylists()
        smart.insert(SmartPlaylistEntry(name = "İkinci", rules = "", createdAt = 2))
        smart.insert(SmartPlaylistEntry(name = "Birinci", rules = "", createdAt = 1))
        assertEquals(listOf("Birinci", "İkinci"), smart.all().map { it.name })
    }

    /** S-613: a device's latency profile: seen at 0, set or measured, deleted; a backup's rows over these. */
    @Test
    fun latencyProfilesArePerOutputDevice() {
        val profiles = db.latencyProfiles()
        val buds = LatencyProfileEntry(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")
        profiles.seen(buds)
        profiles.seen(LatencyProfileEntry(2, "Speaker"))
        assertEquals(0, profiles.get(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")!!.delayMs)
        // the speaker's own measurement is kept apart from its delay
        profiles.save(LatencyProfileEntry(2, "Speaker", measured = true, spreadMs = 2, calibrationMs = 12, updatedAt = 5))
        profiles.get(2, "Speaker", "")!!.let {
            assertEquals(0, it.delayMs)
            assertEquals(12, it.calibrationMs)
        }
        profiles.save(buds.copy(delayMs = 180, measured = true, spreadMs = 4, updatedAt = 10))
        // seen again: the delay stays
        profiles.seen(buds)
        profiles.get(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")!!.let {
            assertEquals(180, it.delayMs)
            assertTrue(it.measured)
            assertEquals(4, it.spreadMs)
        }
        // the list: by kind of device, then name
        assertEquals(listOf("Speaker", "Galaxy Buds2 Pro"), runBlocking { profiles.observe().first() }.map { it.deviceName })
        profiles.restore(listOf(buds.copy(delayMs = 200, updatedAt = 20), LatencyProfileEntry(29, "Denon AVR-X2800H", delayMs = 90)))
        assertEquals(200, profiles.get(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")!!.delayMs)
        assertEquals(3, profiles.all().size)
        profiles.delete(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF")
        assertNull(profiles.get(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF"))
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
        // the latest schema is exported by the build that introduces it, then committed
        assumeTrue("schema ${NagmedarDatabase.VERSION} not exported yet", schemaExported(NagmedarDatabase.VERSION))
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO media_settings (media_uri, audio_delay_ms, updated_at) VALUES ('file:///film.mkv', -120, 1)")
        }
        // 1 -> 2 (S-802): the probe cache appears; 2 -> 3 (S-603): the effects column; 3 -> 4 (S-604):
        // the subtitles' speed; the settings stay
        helper.runMigrationsAndValidate(TEST_DB, NagmedarDatabase.VERSION, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT audio_delay_ms, effects, subtitle_rate FROM media_settings WHERE media_uri = 'file:///film.mkv'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(-120L, c.getLong(0))
                assertTrue("no effects remembered yet", c.isNull(1))
                assertTrue("no subtitle speed remembered yet", c.isNull(2))
            }
            db.execSQL(
                "INSERT INTO media_tech (media_uri, file_size, modified_at, probe_version, json, probed_at) " +
                    "VALUES ('file:///film.mkv', 1, 2, 1, '{}', 3)",
            )
            db.execSQL("UPDATE media_settings SET effects = '{\"gain\":150}' WHERE media_uri = 'file:///film.mkv'")
            // 5 -> 6 (S-808): the ratings and play counts
            db.execSQL(
                "INSERT INTO track_stats (track_key, rating, play_count, skip_count, first_played_at, last_played_at, updated_at) " +
                    "VALUES ('file:///neva.flac', 4, 2, 0, 1, 2, 2)",
            )
            // 6 -> 7 (S-805): the smart lists
            db.execSQL(
                "INSERT INTO smart_playlist (name, rules, match_all, sort, limit_count, created_at, updated_at) " +
                    "VALUES ('En sevilenler', 'rating|at_least|4', 1, 'rating:false', NULL, 1, 1)",
            )
            // 7 -> 8 (S-1105): the radio stations' country, language and votes
            db.execSQL(
                "INSERT INTO radio_station (station_uuid, name, stream_url, bitrate, favorite, last_played_at, country, language, votes, last_voted_at) " +
                    "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 128, 1, 2, 'Türkiye', 'turkish', 40, 3)",
            )
            // 8 -> 9 (S-613): the latency profiles
            db.execSQL(
                "INSERT INTO latency_profile (device_type, device_name, device_address, delay_ms, measured, spread_ms, updated_at, calibration_ms) " +
                    "VALUES (8, 'Galaxy Buds2 Pro', 'AA:BB:CC:DD:EE:FF', 180, 1, 4, 1, NULL)",
            )
            // 9 -> 10 (S-1301): the saved network servers, their sign-in sealed
            db.execSQL(
                "INSERT INTO network_server (name, url, credentials, created_at, last_used_at) " +
                    "VALUES ('NAS', 'smb://nas.local/Filmler', x'0102', 1, 0)",
            )
        }
    }

    @Test
    fun radioStationsKeepTheirRowsFromSeven() {
        assumeTrue("schema 7 not exported yet", schemaExported(7))
        assumeTrue("schema 8 not exported yet", schemaExported(8))
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO radio_station (station_uuid, name, stream_url, bitrate, favorite, last_played_at) " +
                    "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 128, 1, 2)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 8, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, favorite, last_played_at, country, language, votes, last_voted_at FROM radio_station").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Radyo", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(2L, c.getLong(2))
                assertTrue("no country yet", c.isNull(3))
                assertTrue("no language yet", c.isNull(4))
                assertEquals(0, c.getInt(5))
                assertEquals(0L, c.getLong(6))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
