package app.nagmedar.ui.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.TagField
import app.nagmedar.tags.batch.TrackNumbering
import app.nagmedar.tags.lookup.AlbumProposal
import app.nagmedar.tags.lookup.FieldProposal
import app.nagmedar.tags.lookup.LookupCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.3.0: the batch's tools and the lookups' results, as the editor takes them: changes to save, nothing written before. */
@RunWith(AndroidJUnit4::class)
class TagToolsModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fake = FakeTagFiles(scope = scope)
    private val tags = fake.tags
    private val said = mutableListOf<TagMessage>()

    @After
    fun tearDown() {
        TagSessions.forget()
        scope.cancel()
    }

    private fun editor(vararg names: String, lengths: Map<String, Long> = emptyMap()): TagEditorModel =
        TagEditorModel(names.map { sampleTarget(it, durationMs = lengths[it]) }, fake.files).also {
            it.say = { message -> said += message }
            it.load()
        }

    private fun twoAlbums() {
        tags.put(sampleRef("01 Bir Sana Bir Bana.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar CD1"), "ALBUMARTIST" to listOf("Moğollar"))
        tags.put(sampleRef("02 Garip.flac"), "TITLE" to listOf("Garip"), "ARTIST" to listOf("MoÄŸollar"), "ALBUM" to listOf("Moğollar CD2"), "ALBUMARTIST" to listOf("Moğollar"))
        tags.put(sampleRef("03 Haliç.mp3"), "TITLE" to listOf("Haliç"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar CD2"), "ALBUMARTIST" to listOf("Moğollar"))
    }

    private val names = arrayOf("01 Bir Sana Bir Bana.flac", "02 Garip.flac", "03 Haliç.mp3")

    @Test
    fun theTracksAreNumberedInTheirOrder() {
        twoAlbums()
        val editor = editor(*names)
        assertFalse(editor.dirty)
        editor.number(TrackNumbering(startAt = 1, writeTotal = true))
        assertTrue(editor.dirty)
        editor.save()
        assertEquals(listOf("1"), tags.tags(sampleRef(names[0]))["TRACKNUMBER"])
        assertEquals(listOf("3"), tags.tags(sampleRef(names[0]))["TRACKTOTAL"])
        assertEquals(listOf("3/3"), tags.tags(sampleRef(names[2]))["TRACKNUMBER"])
        // the numbering went with the save
        assertEquals(null, editor.numbering)
    }

    @Test
    fun tagsAreReadFromTheFilesNames() {
        twoAlbums()
        val editor = editor(*names)
        editor.choosePattern("{track} {title}")
        editor.readNames(true)
        assertEquals("01 Bir Sana Bir Bana.flac", editor.patternPreview.first().first)
        assertEquals(listOf("01"), editor.patternPreview.first().second?.get("TRACKNUMBER"))
        editor.save()
        assertEquals(listOf("Bir Sana Bir Bana"), tags.tags(sampleRef(names[0]))["TITLE"])
        assertEquals(listOf("02"), tags.tags(sampleRef(names[1]))["TRACKNUMBER"])
        // a pattern that is not one reads nothing, and is no change
        editor.choosePattern("{nothing}")
        editor.readNames(true)
        assertFalse(editor.dirty)
    }

    @Test
    fun eachFilesGarbledTextIsPutRightItsOwnWay() {
        twoAlbums()
        val editor = editor(*names)
        assertEquals(1, editor.garbledFiles)
        assertEquals(Triple("ARTIST", "MoÄŸollar", "Moğollar"), editor.garbledExamples.single())
        editor.fixGarbledEach(true)
        editor.save()
        assertEquals(listOf("Moğollar"), tags.tags(sampleRef(names[1]))["ARTIST"])
    }

    @Test
    fun theDiscsOfOneReleaseAreMadeOneAlbum() {
        twoAlbums()
        val editor = editor(*names)
        val merge = assertNotNull(editor.merge).let { editor.merge!! }
        assertEquals(2, merge.albums.size)
        assertEquals("Moğollar", merge.title)
        editor.mergeAlbums(merge.title, merge.artists, discNumbers = true)
        editor.save()
        names.forEach { assertEquals(it, listOf("Moğollar"), tags.tags(sampleRef(it))["ALBUM"]) }
        assertEquals(listOf("1"), tags.tags(sampleRef(names[0]))["DISCNUMBER"])
        assertEquals(listOf("2"), tags.tags(sampleRef(names[1]))["DISCNUMBER"])
        assertEquals(listOf("2"), tags.tags(sampleRef(names[2]))["DISCNUMBER"])
    }

    @Test
    fun anAlbumFoundFillsItsFieldsAndNumbersTheSongsOnIt() {
        twoAlbums()
        // the library knows how long each song is: a song is found on the album by its title and its length
        val editor = editor(*names, lengths = mapOf(names[0] to 212_000L, names[1] to 251_000L, names[2] to 263_000L))
        fun track(title: String, number: Int, length: Long) = LookupCandidate(
            providerId = "itunes", providerName = "iTunes", id = title, title = title, artists = listOf("Moğollar"),
            album = "Moğollar", albumArtists = listOf("Moğollar"), date = "1971", trackNumber = number, trackTotal = 10, discNumber = 1, discTotal = 1,
            durationMs = length,
        )
        val proposal = AlbumProposal(
            id = "moğollar", album = "Moğollar", albumArtists = listOf("Moğollar"), date = "1971", genres = listOf("Anadolu rock"),
            trackCount = 10, discTotal = 1, artworkUrl = null, artworkThumbnailUrl = null, sources = listOf("iTunes"),
            tracks = listOf(track("Bir Sana Bir Bana", 1, 212_400), track("Garip", 4, 250_200), track("Haliç", 9, 401_000)), songsFound = 2, score = 0.9,
        )
        editor.useAlbum(proposal)
        assertEquals(listOf("Moğollar"), editor.field(TagField.Album.key).values)
        assertEquals(listOf("1971"), editor.field(TagField.Date.key).values)
        editor.save()
        assertEquals(listOf("4"), tags.tags(sampleRef(names[1]))["TRACKNUMBER"])
        assertEquals(listOf("10"), tags.tags(sampleRef(names[1]))["TRACKTOTAL"])
        assertEquals(listOf("Anadolu rock"), tags.tags(sampleRef(names[2]))["GENRE"])
        assertEquals(listOf("1"), tags.tags(sampleRef(names[0]))["TRACKNUMBER"])
        // a song of the same title but another length is another recording: its track is not guessed
        assertTrue(tags.tags(sampleRef(names[2]))["TRACKNUMBER"].isEmpty())
    }

    @Test
    fun aSongsDetailsFoundBecomeChangesNotWrites() {
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bohemian Rhapsody"), "ARTIST" to listOf("Queen"))
        val editor = editor("01.flac")
        assertEquals("Bohemian Rhapsody", editor.lookupQuery().title)
        val found = LookupCandidate(
            providerId = "musicbrainz", providerName = "MusicBrainz", id = "rec", title = "Bohemian Rhapsody", artists = listOf("Queen"),
            album = "A Night at the Opera", date = "1975-11-21", trackNumber = 11, trackTotal = 12, identifiers = mapOf("MUSICBRAINZ_TRACKID" to "rec"),
        )
        val proposals = editor.proposals(found)
        // the title is as the file has it: proposed, not differing
        assertFalse(proposals.single { it.key == "TITLE" }.differs)
        val chosen = proposals.filter { it.differs }.associate { it.key to it.proposed }
        editor.applyFields(chosen)
        assertTrue(editor.dirty)
        assertTrue(fake.tags.written.isEmpty())
        assertEquals("A Night at the Opera", editor.field(TagField.Album.key).text)
        assertEquals(PositionState("11", "12", changed = true), editor.position(TagField.TrackNumber))
        editor.save()
        assertEquals(listOf("rec"), tags.tags(sampleRef("01.flac"))["MUSICBRAINZ_TRACKID"])
    }

    @Test
    fun aCoverIsLookedForAsTheFilesWillBe() {
        twoAlbums()
        val editor = editor(*names)
        editor.set(TagField.Album.key, listOf("Moğollar"))
        val request = editor.coverRequest()
        assertEquals("Moğollar", request.album)
        assertEquals("Bir Sana Bir Bana", request.title)
        assertEquals("Moğollar", editor.albumQuery().album)
        assertEquals(3, editor.albumQuery().songs.size)
    }

    @Test
    fun whatAddsToTheFileIsCheckedAtFirstWhatTakesFromItIsNot() {
        fun proposal(field: TagField, now: List<String>, found: List<String>) = FieldProposal(field.key, field, now, found)
        // adds: a field the file lacks, another album, more artists, a fuller date
        assertTrue(proposal(TagField.Genre, emptyList(), listOf("Anadolu rock")).takenAtFirst)
        assertTrue(proposal(TagField.Album, listOf("Moğollar CD1"), listOf("Moğollar")).takenAtFirst)
        assertTrue(proposal(TagField.Artist, listOf("Moğollar"), listOf("Moğollar", "Cahit Berkay")).takenAtFirst)
        assertTrue(proposal(TagField.Date, listOf("1971"), listOf("1971-03-01")).takenAtFirst)
        // takes: fewer artists, a shorter date, the same in other letters, nothing new
        assertFalse(proposal(TagField.Artist, listOf("Moğollar", "Cahit Berkay"), listOf("Moğollar")).takenAtFirst)
        assertFalse(proposal(TagField.Date, listOf("1971-03-01"), listOf("1971")).takenAtFirst)
        assertFalse(proposal(TagField.Genre, listOf("Anadolu rock"), listOf("Anadolu Rock")).takenAtFirst)
        assertFalse(proposal(TagField.Title, listOf("Garip"), listOf("Garip")).takenAtFirst)
    }
}
