package app.nagmedar.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
import java.io.File

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

    /** S-1101: the subscriptions' grid counts episodes, and a refresh's unplayed ones are the new ones. */
    @Test
    fun subscriptionsAreCountedAndTheirNewEpisodesTold() {
        val podcasts = db.podcasts()
        fun episode(guid: String, at: Long) = PodcastEpisode(
            feedId = 0, guid = guid, title = guid, audioUrl = "https://example.org/$guid.mp3", publishedAt = at, addedAt = 1_000,
            sizeBytes = 4_200_000, chaptersUrl = "https://example.org/$guid.json", transcriptUrl = "https://example.org/$guid.vtt",
            transcriptType = "text/vtt", season = 2, episodeNumber = 5,
        )
        val id = podcasts.subscribeWith(
            PodcastFeed(
                feedUrl = "https://example.org/a.xml", title = "Zebra", subscribedAt = 1_000, podcastGuid = "6a1b6e2b-8f1c-5c4e-9f4a-6d2b1f0e5a11",
                fundingUrl = "https://example.org/destek", fundingMessage = "Destek olun", link = "https://example.org",
            ),
            listOf(episode("a", 10), episode("b", 20)),
        )
        podcasts.subscribeWith(PodcastFeed(feedUrl = "https://example.org/b.xml", title = "ağaç", subscribedAt = 1_000), emptyList())
        // the first fetch's episodes are not new
        assertEquals(listOf("ağaç" to 0, "Zebra" to 2), runBlocking { podcasts.summaries().first() }.map { it.feed.title to it.episodeCount })
        assertEquals(listOf(0, 0), runBlocking { podcasts.summaries().first() }.map { it.newCount })

        // a refresh adds one; played, it is not new either
        podcasts.refreshFeed(podcasts.feed(id)!!, listOf(episode("c", 30).copy(addedAt = 5_000), episode("d", 40).copy(addedAt = 5_000)))
        val zebra = { runBlocking { podcasts.summaries().first() }.single { it.feed.id == id } }
        assertEquals(4, zebra().episodeCount)
        assertEquals(2, zebra().newCount)
        podcasts.setPlayed(podcasts.episodeList(id).first { it.guid == "d" }.id, true)
        assertEquals(1, zebra().newCount)
        assertTrue(podcasts.episodeList(id).first { it.guid == "d" }.played)

        // what the feed said beyond the basics comes back whole
        podcasts.feed(id)!!.let {
            assertEquals("6a1b6e2b-8f1c-5c4e-9f4a-6d2b1f0e5a11", it.podcastGuid)
            assertEquals("Destek olun", it.fundingMessage)
        }
        podcasts.episodeList(id).first { it.guid == "a" }.let {
            assertEquals(4_200_000L, it.sizeBytes)
            assertEquals("text/vtt", it.transcriptType)
            assertEquals(2, it.season)
            assertEquals(5, it.episodeNumber)
        }
        assertEquals(listOf("ağaç", "Zebra"), podcasts.feedList().map { it.title })
        assertNull(podcasts.feed(999))
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

    private fun track(session: String, at: Long, uri: String, size: Long = 1_000, started: Long = 100) = RadioRecording(
        sessionId = session, stationKey = "uuid-1", stationName = "Radyo", streamUrl = "https://radio.example/a",
        fileUri = uri, fileName = uri.substringAfterLast('/'), artist = "Sanatçı", title = "Parça", durationMs = 180_000,
        sizeBytes = size, complete = true, format = "mp3", startedAt = at, sessionStartedAt = started,
    )

    @Test
    fun recordedTracksAreListedBySessionAndTheBinIsLeftOut() = runBlocking {
        val dao = db.radioRecordings()
        val a = dao.add(track("s1", 100, "file:///a/1.mp3", started = 100))
        val b = dao.add(track("s1", 200, "file:///a/2.mp3", started = 100))
        dao.add(track("s2", 300, "file:///a/3.mp3", started = 300))
        // the newest session first, its tracks in the order they were recorded
        assertEquals(listOf("3.mp3", "1.mp3", "2.mp3"), dao.tracks().first().map { it.fileName })
        dao.trash(listOf(b), at = 400)
        assertEquals(listOf("3.mp3", "1.mp3"), dao.tracks().first().map { it.fileName })
        assertEquals(listOf(b), dao.trashed().map { it.id })
        // a track in the bin still takes room until its file is deleted
        assertEquals(3_000L, dao.usedBytes())
        dao.trash(listOf(b), at = 0)
        assertEquals(3, dao.tracks().first().size)
        dao.delete(listOf(a, b))
        assertEquals(listOf("3.mp3"), dao.tracks().first().map { it.fileName })
        assertEquals(1_000L, dao.used().first())
    }

    @Test
    fun aFileIsRecordedOnceAndRenamingKeepsTheRow() {
        val dao = db.radioRecordings()
        val id = dao.add(track("s1", 100, "file:///a/1.mp3"))
        // the same file again replaces its row instead of listing it twice
        dao.add(track("s1", 100, "file:///a/1.mp3", size = 2_000))
        assertEquals(1, dao.sessionTracks("s1").size)
        assertEquals(2_000L, dao.usedBytes())
        dao.rename(dao.sessionTracks("s1").single().id, "file:///a/yeni.mp3", "yeni.mp3")
        assertEquals("yeni.mp3", dao.sessionTracks("s1").single().fileName)
        assertNotNull(dao.track(dao.sessionTracks("s1").single().id))
        assertTrue(id > 0)
    }

    @Test
    fun theOldestTracksArePrunedFirstAndTheBinBeforeThem() {
        val dao = db.radioRecordings()
        dao.add(track("old", 100, "file:///a/old.mp3", started = 100))
        dao.add(track("new", 900, "file:///a/new.mp3", started = 900))
        val binned = dao.add(track("mid", 500, "file:///a/mid.mp3", started = 500))
        dao.trash(listOf(binned), at = 1_000)
        assertEquals(listOf("mid.mp3", "old.mp3", "new.mp3"), dao.oldestFirst().map { it.fileName })
    }

    @Test
    fun scheduledRecordingsAreSavedUpdatedAndDeleted() = runBlocking {
        val dao = db.radioRecordings()
        val entry = RadioScheduleEntry(
            stationKey = "uuid-1", stationName = "Radyo", streamUrl = "https://radio.example/a", startAt = 1_000,
            hour = 7, minute = 30, durationMinutes = 60, repeat = "Weekdays", weekday = 1, createdAt = 5,
        )
        val id = dao.keepSchedule(entry)
        assertTrue(id > 0)
        assertEquals(listOf(id), dao.schedules().first().map { it.id })
        // saving it again with its id updates the row and answers the same id
        assertEquals(id, dao.keepSchedule(entry.copy(id = id, minute = 45)))
        assertEquals(45, dao.schedule(id)!!.minute)
        assertEquals(1, dao.scheduleList().size)
        dao.enableSchedule(id, false)
        assertEquals(false, dao.schedule(id)!!.enabled)
        dao.markScheduleStarted(id, 9_000)
        assertEquals(9_000L, dao.schedule(id)!!.lastStartedAt)
        dao.deleteSchedule(id)
        assertNull(dao.schedule(id))
    }

    @Test
    fun scrobblesWaitPerServiceOldestFirstAndOnlyOnce() {
        val queue = db.scrobbles()
        fun entry(service: String, track: String, at: Long) = ScrobbleEntry(service = service, artist = "Ada Karan", track = track, album = "Gece Mavisi", durationSec = 241, listenedAt = at, queuedAt = at)
        assertTrue(queue.add(entry("lastfm", "Sabaha Kadar", 200)) > 0)
        assertTrue(queue.add(entry("lastfm", "Uzak", 100)) > 0)
        assertTrue(queue.add(entry("listenbrainz", "Sabaha Kadar", 200)) > 0)
        // the same track at the same time, for the same service: once
        assertEquals(-1L, queue.add(entry("lastfm", "Sabaha Kadar", 200)))
        assertEquals(listOf("Uzak", "Sabaha Kadar"), queue.pending("lastfm", 50).map { it.track })
        assertEquals(listOf("Uzak"), queue.pending("lastfm", 1).map { it.track })
        assertEquals(mapOf("lastfm" to 2, "listenbrainz" to 1), runBlocking { queue.counts().first() }.associate { it.service to it.count })

        val first = queue.pending("lastfm", 50)
        queue.failed(listOf(first[0].id))
        assertEquals(listOf(1, 0), queue.pending("lastfm", 50).map { it.attempts })
        queue.remove(listOf(first[0].id))
        assertEquals(1, queue.count("lastfm"))
        // what was heard long ago is dropped, one service's queue is cleared on its own
        assertEquals(1, queue.dropOlderThan("lastfm", 300))
        assertEquals(0, queue.count("lastfm"))
        assertEquals(1, queue.clear("listenbrainz"))
        assertTrue(runBlocking { queue.counts().first() }.isEmpty())
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
        // S-1005: and the originals the lyrics editor replaced
        lyrics.save(LyricsEntry("t4", LyricsSource.ORIGINAL_TAG, synced = false, text = "özgün"))
        lyrics.save(LyricsEntry("t5", LyricsSource.ORIGINAL_FILE, synced = true, text = "[00:01.00]özgün"))
        assertEquals(listOf("t2", "t3", "t4", "t5"), lyrics.kept().map { it.trackKey })
        lyrics.saveAll(listOf(LyricsEntry("t1", LyricsSource.LRCLIB, synced = true, text = "[00:01.00]bir", offsetMs = -200, fetchedAt = 1)))
        assertEquals(-200L, lyrics.forTrack("t1").single().offsetMs)

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
        helper.runMigrationsAndValidate(TEST_DB, 20, true, *NagmedarDatabase.MIGRATIONS).use { db ->
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
            // 12 -> 13 (S-1008): the scrobbles waiting, each track once per service and time
            db.execSQL(
                "INSERT INTO scrobble_queue (service, artist, track, album, album_artist, duration_sec, listened_at, attempts, queued_at) " +
                    "VALUES ('lastfm', 'Ada Karan', 'Sabaha Kadar', NULL, NULL, 241, 1000, 0, 1000)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO scrobble_queue (service, artist, track, album, album_artist, duration_sec, listened_at, attempts, queued_at) " +
                    "VALUES ('lastfm', 'Ada Karan', 'Sabaha Kadar', NULL, NULL, 241, 1000, 0, 1001)",
            )
            db.query("SELECT COUNT(*) FROM scrobble_queue").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            // 14 -> 15 (S-1107): the radio recordings and their schedules
            db.execSQL(
                "INSERT INTO radio_recording (session_id, station_key, station_name, stream_url, file_uri, file_name, " +
                    "duration_ms, size_bytes, complete, format, started_at, session_started_at, trashed_at) " +
                    "VALUES ('s1', 'uuid-1', 'Radyo', 'https://radio.example/a', 'file:///a.mp3', 'a.mp3', 1, 2, 1, 'mp3', 3, 3, 0)",
            )
            db.execSQL(
                "INSERT INTO radio_schedule (station_key, station_name, stream_url, start_at, hour, minute, duration_minutes, " +
                    "repeat, weekday, enabled, last_started_at, created_at) " +
                    "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 1, 7, 30, 60, 'Daily', 1, 1, 0, 1)",
            )
            // 15 -> 16 (S-1206): the remux queue
            db.execSQL(
                "INSERT INTO remux_job (input_uri, input_name, input_size, output_uri, output_name, plan, state, progress, " +
                    "media_seconds, total_seconds, bytes_out, attempts, created_at, events, cancel_requested, owns_output) " +
                    "VALUES ('content://a', 'a.mkv', 1, 'content://b', 'b.mkv', '{}', 'queued', 0, 0, 0, 0, 0, 1, '', 0, 0)",
            )
            // 16 -> 17 (S-1107): the runs of a scheduled recording
            db.execSQL(
                "INSERT INTO radio_schedule_run (schedule_id, window_start, window_end, status, interruptions, session_id, updated_at) " +
                    "VALUES (1, 1, 2, 'recorded', 0, NULL, 2)",
            )
            // 19 -> 20 (S-1210): the remux queue's order, the job queued before kept first
            db.query("SELECT position FROM remux_job WHERE input_uri = 'content://a'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
            db.execSQL("UPDATE remux_job SET position = 2 WHERE input_uri = 'content://a'")
        }
        // 20 -> 21 (1.0): the removed features' tables go, every other row stays
        helper.runMigrationsAndValidate(TEST_DB, NagmedarDatabase.VERSION, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            assertEquals(emptyList<String>(), tables(db).filter { it in NagmedarDatabase.RETIRED_TABLES })
            for (table in listOf("media_tech", "track_stats", "smart_playlist", "radio_station", "latency_profile", "network_server",
                "scrobble_queue", "radio_recording", "radio_schedule", "radio_schedule_run")) {
                assertEquals(table, 1, count(db, "SELECT COUNT(*) FROM $table"))
            }
        }
    }

    /**
     * 20 → 21 on the user's database: one with rows in every table the removed features used, and the music, podcasts
     * and radio's, opens with the app's own migrations; the tables go, the rows that stay are all there, and the files
     * the jobs held a permission to are noted for the app to let go of.
     */
    @Test
    fun aTwentyWithTheRemovedFeaturesRowsOpensAsTwentyOne() {
        assumeTrue("schema 20 not exported yet", schemaExported(20))
        assumeTrue("schema ${NagmedarDatabase.VERSION} not exported yet", schemaExported(NagmedarDatabase.VERSION))
        helper.createDatabase(TEST_DB, 20).use { db ->
            db.execSQL("INSERT INTO media_settings (media_uri, audio_delay_ms, subtitle_encoding, updated_at) VALUES ('file:///film.mkv', -120, 'Windows-1254', 1)")
            db.execSQL(
                "INSERT INTO transcode_profile (name, container, video_codec, video_kbps, max_height, fps, audio_codec, audio_kbps, channels, " +
                    "sample_rate, subtitles, created_at, updated_at) VALUES ('TV', 'mp4', 'h264', 4000, 1080, 0, 'aac', 192, 2, 48000, 'burn', 1, 1)",
            )
            db.query("SELECT * FROM transcode_job").use { c ->
                val columns = c.columnNames.toList()
                val values = columns.map { column ->
                    when (column) {
                        "id" -> "NULL"
                        "input_uri" -> "'content://docs/document/eski.mkv'"
                        "output_uri" -> "'content://docs/document/eski.mp4'"
                        "input_name", "output_name", "profile", "state", "error" -> "'x'"
                        "started_at", "finished_at" -> "NULL"
                        else -> "0"
                    }
                }
                db.execSQL("INSERT INTO transcode_job (${columns.joinToString()}) VALUES (${values.joinToString()})")
            }
            remuxJob(db, 3)
            db.execSQL("UPDATE remux_job SET output_uri = 'content://docs/document/film%20(remux).mkv', input_uri = 'content://docs/document/film.mkv'")
            db.execSQL("INSERT INTO film_info (query_key, kind, tmdb_id, title, language, manual, fetched_at) VALUES ('kuzey|2024', 'movie', 7, 'Kuzey', 'tr', 0, 1)")
            db.execSQL("INSERT INTO film_episode (show_id, season, episode, language, fetched_at) VALUES (7, 1, 1, 'tr', 1)")
            // the user's music, podcasts and radio
            db.execSQL(
                "INSERT INTO track_stats (track_key, rating, play_count, skip_count, first_played_at, last_played_at, updated_at) " +
                    "VALUES ('file:///neva.flac', 4, 2, 0, 1, 2, 2)",
            )
            db.execSQL("INSERT INTO smart_playlist (name, rules, match_all, sort, limit_count, created_at, updated_at) VALUES ('En sevilenler', '', 1, NULL, NULL, 1, 1)")
            feed(db)
            db.execSQL(
                "INSERT INTO podcast_episode (feed_id, guid, title, audio_url, duration_ms, published_at, position_ms, played, download_state) " +
                    "VALUES (1, 'ep-1', 'Bölüm 1', 'https://example.org/1.mp3', 60000, 9, 1500, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO radio_station (station_uuid, name, stream_url, bitrate, favorite, last_played_at, votes, last_voted_at) " +
                    "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 128, 1, 2, 0, 0)",
            )
            schedule(db)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val note = File(context.cacheDir, "retired-grants-test").apply { delete() }
        val room = Room.databaseBuilder(context, NagmedarDatabase::class.java, TEST_DB)
            .addMigrations(*NagmedarDatabase.migrations { note })
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, room.trackStats().get("file:///neva.flac")!!.playCount)
            assertEquals(listOf("En sevilenler"), room.smartPlaylists().all().map { it.name })
            assertEquals(listOf("Radyo"), runBlocking { room.radio().favorites().first() }.map { it.name })
            val db = room.openHelper.readableDatabase
            assertEquals(1, count(db, "SELECT COUNT(*) FROM podcast_feed WHERE title = 'Nağmedar Podcast'"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM podcast_episode WHERE guid = 'ep-1' AND position_ms = 1500"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM radio_schedule"))
            assertEquals(emptyList<String>(), tables(db).filter { it in NagmedarDatabase.RETIRED_TABLES })
        } finally {
            room.close()
        }
        assertEquals(
            listOf(
                "content://docs/document/eski.mkv", "content://docs/document/eski.mp4",
                "content://docs/document/film%20(remux).mkv", "content://docs/document/film.mkv",
            ),
            note.readLines().filter { it.isNotBlank() },
        )
    }

    /** 20 → 21 where a pull request's build left some of the dropped tables out: nothing to drop is no failure. */
    @Test
    fun aTwentyWithoutSomeOfTheTablesMigratesAllTheSame() {
        assumeTrue("schema 20 not exported yet", schemaExported(20))
        assumeTrue("schema ${NagmedarDatabase.VERSION} not exported yet", schemaExported(NagmedarDatabase.VERSION))
        helper.createDatabase(TEST_DB, 20).use { db ->
            db.execSQL("DROP TABLE remux_job")
            db.execSQL("DROP TABLE film_episode")
            feed(db)
        }
        helper.runMigrationsAndValidate(TEST_DB, NagmedarDatabase.VERSION, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM podcast_feed"))
        }
    }

    @Test
    fun radioRecordingsAppearInFifteen() {
        assumeTrue("schema 14 not exported yet", schemaExported(14))
        assumeTrue("schema 15 not exported yet", schemaExported(15))
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO radio_station (station_uuid, name, stream_url, bitrate, favorite, last_played_at, votes, last_voted_at) " +
                    "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 128, 1, 2, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 15, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            // the stations stay; the new tables are there, empty
            db.query("SELECT name FROM radio_station").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Radyo", c.getString(0))
            }
            db.query("SELECT COUNT(*) FROM radio_recording").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM radio_schedule").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
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

    /** 10 → 11 (S-1101): a subscription made before keeps its rows, and the new columns start empty. */
    @Test
    fun podcastRowsSurviveTheirNewColumns() {
        assumeTrue("schema 10 not exported yet", schemaExported(10))
        assumeTrue("schema 11 not exported yet", schemaExported(11))
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                "INSERT INTO podcast_feed (id, feed_url, title, refreshed_at, auto_download, subscribed_at) " +
                    "VALUES (1, 'https://example.org/feed.xml', 'Nağmedar Podcast', 5, 0, 7)",
            )
            db.execSQL(
                "INSERT INTO podcast_episode (feed_id, guid, title, audio_url, duration_ms, published_at, position_ms, played, download_state) " +
                    "VALUES (1, 'ep-1', 'Bölüm 1', 'https://example.org/1.mp3', 60000, 9, 1500, 1, 0)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 11, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT title, subscribed_at, link, podcast_guid, funding_url, alias_keys FROM podcast_feed WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Nağmedar Podcast", c.getString(0))
                assertEquals(7L, c.getLong(1))
                assertTrue(c.isNull(2) && c.isNull(3) && c.isNull(4))
                assertEquals("", c.getString(5))
            }
            db.query("SELECT played, position_ms, added_at, size_bytes, image_url, season, chapters_url FROM podcast_episode WHERE guid = 'ep-1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(1500L, c.getLong(1))
                assertEquals("not new: it was there before the column", 0L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertTrue(c.isNull(4) && c.isNull(5) && c.isNull(6))
            }
        }
    }

    /** 19 → 20 (S-1210): the jobs queued before keep the order they were queued in: each one's position is its id. */
    @Test
    fun remuxJobsKeepTheirOrderFromNineteen() {
        assumeTrue("schema 19 not exported yet", schemaExported(19))
        assumeTrue("schema 20 not exported yet", schemaExported(20))
        helper.createDatabase(TEST_DB, 19).use { db ->
            remuxJob(db, 3)
            remuxJob(db, 7)
        }
        helper.runMigrationsAndValidate(TEST_DB, 20, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            assertEquals(listOf(3L to 3L, 7L to 7L), positions(db))
        }
    }

    /** 15 → 20: a scheduled recording and a show from 15 cross every step, keep their rows and get their new columns at 0. */
    @Test
    fun scheduleAndShowCrossFromFifteen() {
        assumeTrue("schema 15 not exported yet", schemaExported(15))
        assumeTrue("schema 20 not exported yet", schemaExported(20))
        helper.createDatabase(TEST_DB, 15).use { db ->
            schedule(db)
            feed(db)
        }
        helper.runMigrationsAndValidate(TEST_DB, 20, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            db.query("SELECT station_name, checked_at FROM radio_schedule").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Radyo", c.getString(0))
                assertEquals(0L, c.getLong(1))
            }
            db.query("SELECT title, volume_boost FROM podcast_feed").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Nağmedar Podcast", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    /**
     * 16 or 17 as pull requests' debug builds left them before their numbers moved (16: the radio's schedule runs, the
     * podcasts' volume boost or the film information; 17: the remux queue's order): each ends as 20, its rows and an
     * order already there kept.
     */
    @Test
    fun aPullRequestBuildsDatabaseEndsAsTwenty() {
        assumeTrue("schema 15 not exported yet", schemaExported(15))
        assumeTrue("schema 16 not exported yet", schemaExported(16))
        assumeTrue("schema 20 not exported yet", schemaExported(20))
        fun step(from: Int) = NagmedarDatabase.MIGRATIONS.single { it.startVersion == from }
        // what 16 → 17, 17 → 18 and 18 → 19 add is what those builds called 15 → 16
        for ((build, theirs) in listOf("radio" to step(16), "podcast" to step(17), "films" to step(18))) {
            val name = "$build-$TEST_DB"
            helper.createDatabase(name, 15).use { db ->
                schedule(db)
                feed(db)
                theirs.migrate(db)
                db.execSQL("PRAGMA user_version = 16")
            }
            helper.runMigrationsAndValidate(name, 20, true, *NagmedarDatabase.MIGRATIONS).use { db ->
                assertEquals(build, 1, count(db, "SELECT COUNT(*) FROM radio_schedule WHERE checked_at = 0"))
                assertEquals(build, 1, count(db, "SELECT COUNT(*) FROM podcast_feed WHERE volume_boost = 0"))
                assertEquals(build, 0, count(db, "SELECT COUNT(*) FROM remux_job"))
            }
        }
        // S-1205's build: 16 with the queue's order, a job moved up by hand
        val name = "order-$TEST_DB"
        helper.createDatabase(name, 16).use { db ->
            remuxJob(db, 3)
            remuxJob(db, 7)
            db.execSQL("ALTER TABLE `remux_job` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `remux_job` SET `position` = CASE `id` WHEN 3 THEN 2 ELSE 1 END")
            db.execSQL("PRAGMA user_version = 17")
        }
        helper.runMigrationsAndValidate(name, 20, true, *NagmedarDatabase.MIGRATIONS).use { db ->
            assertEquals(listOf(3L to 2L, 7L to 1L), positions(db))
        }
    }

    private fun schedule(db: SupportSQLiteDatabase) = db.execSQL(
        "INSERT INTO radio_schedule (station_key, station_name, stream_url, start_at, hour, minute, duration_minutes, " +
            "repeat, weekday, enabled, last_started_at, created_at) " +
            "VALUES ('uuid-1', 'Radyo', 'https://radio.example/a', 1, 7, 30, 60, 'Daily', 1, 1, 0, 1)",
    )

    private fun feed(db: SupportSQLiteDatabase) = db.execSQL(
        "INSERT INTO podcast_feed (id, feed_url, title, refreshed_at, auto_download, subscribed_at) " +
            "VALUES (1, 'https://example.org/feed.xml', 'Nağmedar Podcast', 5, 0, 7)",
    )

    private fun remuxJob(db: SupportSQLiteDatabase, id: Int) = db.execSQL(
        "INSERT INTO remux_job (id, input_uri, input_name, input_size, output_uri, output_name, plan, state, progress, " +
            "media_seconds, total_seconds, bytes_out, attempts, created_at, events, cancel_requested, owns_output) " +
            "VALUES ($id, 'content://a$id', 'a$id.mkv', 1, 'content://b$id', 'b$id.mkv', '{}', 'queued', 0, 0, 0, 0, 0, 1, '', 0, 0)",
    )

    private fun positions(db: SupportSQLiteDatabase): List<Pair<Long, Long>> =
        db.query("SELECT id, position FROM remux_job ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) }
        }

    private fun tables(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use { c ->
        assertTrue(c.moveToFirst())
        c.getInt(0)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
