package app.nagmedar.ui.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.PictureType
import app.nagmedar.tags.TagField
import app.nagmedar.tags.TagWriteResult
import app.nagmedar.tags.editor.MultiValueMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.2.0: Metamatik's editor in Nağmedar: only what changed is written, every save is backed up and undone from its
 * message, several files take what is set for all and keep the rest of their own.
 */
@RunWith(AndroidJUnit4::class)
class TagEditorModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fake = FakeTagFiles(scope = scope)
    private val tags = fake.tags
    private val said = mutableListOf<TagMessage>()

    @After
    fun tearDown() {
        TagSessions.forget()
        scope.cancel()
    }

    private fun editor(vararg names: String, style: MultiValueStyle = MultiValueStyle()): TagEditorModel =
        TagEditorModel(names.map { sampleTarget(it) }, fake.files) { style }.also {
            it.say = { message -> said += message }
            it.load()
        }

    private fun oneSong() = tags.put(
        sampleRef("01.flac"),
        "TITLE" to listOf("Bir Sana Bir Bana"),
        "ARTIST" to listOf("Moğollar"),
        "ALBUM" to listOf("Moğollar"),
        "TRACKNUMBER" to listOf("1"),
        "TRACKTOTAL" to listOf("12"),
        "REPLAYGAIN_TRACK_GAIN" to listOf("-6.20 dB"),
        "MUSICBRAINZ_TRACKID" to listOf("8f1c"),
    )

    @Test
    fun aFileIsReadWithEveryTagAndItsAudio() {
        oneSong()
        val editor = editor("01.flac")
        assertEquals(EditorPhase.Ready, editor.phase)
        assertEquals("Bir Sana Bir Bana", editor.field(TagField.Title.key).text)
        assertEquals(listOf("Moğollar"), editor.field(TagField.Artist.key).values)
        assertEquals(PositionState("1", "12"), editor.position(TagField.TrackNumber))
        // what has no place of its own is under "Tüm etiketler"
        assertEquals(listOf("MUSICBRAINZ_TRACKID", "REPLAYGAIN_TRACK_GAIN"), editor.otherKeys)
        assertEquals(44_100, editor.audio?.sampleRateHz)
        assertFalse(editor.dirty)
    }

    @Test
    fun onlyWhatChangedIsWrittenAndTheRestStaysAsItWas() {
        oneSong()
        val editor = editor("01.flac")
        editor.set(TagField.Title.key, listOf("Bir Sana Bir Bana (1971)"))
        editor.set(TagField.Artist.key, listOf("Moğollar", "Cahit Berkay"))
        assertTrue(editor.dirty)
        assertEquals(2, editor.changeCount)
        assertTrue(editor.field(TagField.Title.key).changed)
        editor.save()
        val written = tags.tags(sampleRef("01.flac"))
        assertEquals(listOf("Bir Sana Bir Bana (1971)"), written["TITLE"])
        assertEquals(listOf("Moğollar", "Cahit Berkay"), written["ARTIST"])
        // the tags nobody touched, the ones the editor has no name for too
        assertEquals(listOf("-6.20 dB"), written["REPLAYGAIN_TRACK_GAIN"])
        assertEquals(listOf("8f1c"), written["MUSICBRAINZ_TRACKID"])
        assertEquals(listOf("12"), written["TRACKTOTAL"])
        // the editor shows the file as it is now: nothing waits
        assertFalse(editor.dirty)
        assertFalse(editor.saving)
        assertEquals(listOf(sampleRef("01.flac")), fake.indexer.rescanned)
        assertEquals(1, fake.backups.kept.size)
        val saved = said.single() as TagMessage.Saved
        assertEquals(1, saved.saved)
    }

    @Test
    fun aSaveIsUndoneFromItsMessage() {
        oneSong()
        val editor = editor("01.flac")
        editor.set(TagField.Album.key, listOf("Anadolu Pop"))
        editor.save()
        val undo = (said.last() as TagMessage.Saved).undo!!
        undo()
        assertEquals(listOf("Moğollar"), tags.tags(sampleRef("01.flac"))["ALBUM"])
        assertEquals("Moğollar", editor.field(TagField.Album.key).text)
        assertEquals(TagMessage.Undone(1, 1), said.last())
        // the backup went with it
        assertTrue(fake.backups.kept.isEmpty())
    }

    @Test
    fun fieldsOfSeveralValuesAreJoinedWhenAsked() {
        oneSong()
        val editor = editor("01.flac", style = MultiValueStyle(MultiValueMode.Joined, " / "))
        editor.set(TagField.Artist.key, listOf("Moğollar", "Cahit Berkay"))
        editor.save()
        assertEquals(listOf("Moğollar / Cahit Berkay"), tags.tags(sampleRef("01.flac"))["ARTIST"])
    }

    @Test
    fun aChangeIsTakenBackFieldByFieldOrAllAtOnce() {
        oneSong()
        val editor = editor("01.flac")
        editor.set(TagField.Title.key, listOf("Başka"))
        editor.set(TagField.Genre.key, listOf("Anadolu rock"))
        editor.revert(listOf(TagField.Title.key))
        assertEquals("Bir Sana Bir Bana", editor.field(TagField.Title.key).text)
        assertEquals(1, editor.changeCount)
        editor.revertAll()
        assertFalse(editor.dirty)
        // typed back to what it was is no change either
        editor.set(TagField.Title.key, listOf("Bir Sana Bir Bana"))
        assertFalse(editor.dirty)
    }

    @Test
    fun aPositionIsWrittenAsTheFormatKeepsIt() {
        oneSong()
        tags.put(sampleRef("02.mp3"), "TITLE" to listOf("Garip"), "TRACKNUMBER" to listOf("2/9"))
        val flac = editor("01.flac")
        flac.setPosition(TagField.TrackNumber, "3", "12")
        flac.save()
        assertEquals(listOf("3"), tags.tags(sampleRef("01.flac"))["TRACKNUMBER"])
        assertEquals(listOf("12"), tags.tags(sampleRef("01.flac"))["TRACKTOTAL"])
        val mp3 = editor("02.mp3")
        assertEquals(PositionState("2", "9"), mp3.position(TagField.TrackNumber))
        mp3.setPosition(TagField.TrackNumber, "4", "10")
        assertTrue(mp3.position(TagField.TrackNumber).changed)
        mp3.save()
        // ID3 keeps a track and its total in one field
        assertEquals(listOf("4/10"), tags.tags(sampleRef("02.mp3"))["TRACKNUMBER"])
    }

    @Test
    fun aFileThatMayNotBeWrittenSaysSoAndKeepsTheChanges() {
        oneSong()
        val editor = editor("01.flac")
        editor.set(TagField.Title.key, listOf("Yeni"))
        tags.refuse = TagWriteResult.NeedsAccess
        editor.save()
        assertEquals(TagMessage.NeedsAccess, said.single())
        assertTrue(editor.dirty)
        assertFalse(editor.saving)
        // nothing was written, and no backup is kept for it
        assertTrue(fake.backups.kept.isEmpty())
    }

    @Test
    fun aFileNotThereIsAProblemToShow() {
        val editor = editor("yok.flac")
        assertEquals(EditorPhase.Problem(TagProblem.NotFound), editor.phase)
    }

    @Test
    fun theCoverIsReplacedAndTheOtherPicturesStay() {
        val back = EmbeddedPicture(byteArrayOf(9, 9), "image/png", type = PictureType.BackCover)
        val front = EmbeddedPicture(byteArrayOf(1, 2), "image/jpeg")
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Garip"), pictures = listOf(front, back))
        val editor = editor("01.flac")
        assertEquals(front, editor.cover.picture)
        val chosen = EmbeddedPicture(byteArrayOf(7, 7, 7), "image/jpeg")
        editor.setCover(chosen)
        assertTrue(editor.cover.changed)
        assertEquals(1, editor.changeCount)
        editor.save()
        val pictures = tags.files.getValue(sampleRef("01.flac")).pictures
        assertEquals(listOf(chosen, back), pictures)
        assertEquals(1, fake.coversFound)
        editor.removeCover()
        editor.save()
        assertEquals(listOf(back), tags.files.getValue(sampleRef("01.flac")).pictures)
    }

    @Test
    fun garbledTextIsOfferedPutRight() {
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("MoÄŸollar"), "ALBUM" to listOf("Moðollar"))
        val editor = editor("01.flac")
        assertEquals(mapOf("ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar")), editor.repairs)
        editor.repairText()
        assertEquals("Moğollar", editor.field(TagField.Album.key).text)
        assertTrue(editor.repairs.isEmpty())
        assertEquals(2, editor.changeCount)
    }

    // ------------------------------------------------------------------ several files

    private fun anAlbum() {
        tags.put(sampleRef("01.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"), "TRACKNUMBER" to listOf("1"), "GENRE" to listOf("Rock"))
        tags.put(sampleRef("02.flac"), "TITLE" to listOf("Garip"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"), "TRACKNUMBER" to listOf("2"), "GENRE" to listOf("Anadolu rock"))
        tags.put(sampleRef("03.mp3"), "TITLE" to listOf("Haliç'te Güneşin Batışı"), "ARTIST" to listOf("Moğollar"), "TRACKNUMBER" to listOf("3/3"), "MOOD" to listOf("Hüzünlü"))
    }

    @Test
    fun severalFilesShowWhatTheyShareAndWhereTheyDiffer() {
        anAlbum()
        val editor = editor("01.flac", "02.flac", "03.mp3")
        assertTrue(editor.many)
        assertEquals(3, editor.loaded.size)
        assertEquals(FieldState(listOf("Moğollar")), editor.field(TagField.Artist.key))
        assertTrue(editor.field(TagField.Genre.key).mixed)
        // one file has no album: they differ
        assertTrue(editor.field(TagField.Album.key).mixed)
        assertEquals(FieldState.Empty, editor.field(TagField.Composer.key))
        assertTrue(editor.position(TagField.TrackNumber).numberMixed)
        assertFalse(editor.numberEditable(TagField.TrackNumber))
        assertEquals(listOf("MOOD"), editor.otherKeys)
    }

    @Test
    fun whatIsSetForAllIsWrittenToEachAndTheirOwnStays() {
        anAlbum()
        val editor = editor("01.flac", "02.flac", "03.mp3")
        editor.set(TagField.Album.key, listOf("Moğollar (1971)"))
        editor.set(TagField.AlbumArtist.key, listOf("Moğollar"))
        editor.setPosition(TagField.DiscNumber, "1", "2")
        assertEquals(3, editor.changeCount)
        editor.save()
        for (name in listOf("01.flac", "02.flac", "03.mp3")) {
            val written = tags.tags(sampleRef(name))
            assertEquals(name, listOf("Moğollar (1971)"), written["ALBUM"])
            assertEquals(name, listOf("Moğollar"), written["ALBUMARTIST"])
        }
        // each keeps its own title, genre and track
        assertEquals(listOf("Garip"), tags.tags(sampleRef("02.flac"))["TITLE"])
        assertEquals(listOf("Anadolu rock"), tags.tags(sampleRef("02.flac"))["GENRE"])
        assertEquals(listOf("2"), tags.tags(sampleRef("02.flac"))["TRACKNUMBER"])
        assertEquals(listOf("Hüzünlü"), tags.tags(sampleRef("03.mp3"))["MOOD"])
        // the disc as each format keeps it
        assertEquals(listOf("1"), tags.tags(sampleRef("01.flac"))["DISCNUMBER"])
        assertEquals(listOf("2"), tags.tags(sampleRef("01.flac"))["DISCTOTAL"])
        assertEquals(listOf("1/2"), tags.tags(sampleRef("03.mp3"))["DISCNUMBER"])
        val saved = said.single() as TagMessage.Saved
        assertEquals(3, saved.saved)
        assertEquals(3, saved.total)
        // what they share now is shown, and nothing waits
        assertFalse(editor.dirty)
        assertEquals(FieldState(listOf("Moğollar (1971)")), editor.field(TagField.Album.key))
    }

    @Test
    fun aBatchIsUndoneAllAtOnce() {
        anAlbum()
        val editor = editor("01.flac", "02.flac", "03.mp3")
        editor.set(TagField.Genre.key, listOf("Anadolu rock"))
        editor.save()
        // the second file had it already: nothing was written there, nor backed up
        val saved = said.last() as TagMessage.Saved
        assertEquals(2, saved.saved)
        assertEquals(3, saved.total)
        saved.undo!!()
        assertEquals(listOf("Rock"), tags.tags(sampleRef("01.flac"))["GENRE"])
        assertNull(tags.tags(sampleRef("03.mp3"))["GENRE"].firstOrNull())
        assertEquals(TagMessage.Undone(2, 2), said.last())
        assertTrue(editor.field(TagField.Genre.key).mixed)
    }

    @Test
    fun aFieldEmptiedWhereTheyDifferKeepsTheirsAndOneTheyShareIsCleared() {
        anAlbum()
        val editor = editor("01.flac", "02.flac", "03.mp3")
        editor.set(TagField.Genre.key, listOf("Pop"))
        editor.set(TagField.Genre.key, emptyList())
        assertFalse(editor.dirty)
        editor.set(TagField.Artist.key, emptyList())
        assertTrue(editor.field(TagField.Artist.key).changed)
        editor.save()
        assertTrue(tags.tags(sampleRef("02.flac"))["ARTIST"].isEmpty())
        assertEquals(listOf("Garip"), tags.tags(sampleRef("02.flac"))["TITLE"])
    }

    @Test
    fun oneCoverIsWrittenToEveryFile() {
        anAlbum()
        val editor = editor("01.flac", "02.flac")
        assertTrue(editor.cover.own)
        val cover = EmbeddedPicture(byteArrayOf(4, 2), "image/jpeg")
        editor.setCover(cover)
        editor.save()
        assertEquals(listOf(cover), tags.files.getValue(sampleRef("01.flac")).pictures)
        assertEquals(listOf(cover), tags.files.getValue(sampleRef("02.flac")).pictures)
        assertEquals(2, fake.coversFound)
    }

    @Test
    fun aFileThatDoesNotReadIsLeftOutAndSaid() {
        anAlbum()
        val editor = editor("01.flac", "yok.flac", "02.flac")
        assertEquals(1, editor.skipped)
        assertEquals(2, editor.loaded.size)
    }

    @Test
    fun theEditorWithChangesStaysWhenOtherFilesAreAskedFor() {
        anAlbum()
        val style = { MultiValueStyle() }
        val first = TagSessions.open(fake.files, listOf(sampleTarget("01.flac")), style)
        assertEquals(first, TagSessions.open(fake.files, listOf(sampleTarget("01.flac")), style))
        first.set(TagField.Title.key, listOf("Başka"))
        // held while its changes are not saved
        assertEquals(first, TagSessions.open(fake.files, listOf(sampleTarget("02.flac")), style))
        first.revertAll()
        val second = TagSessions.open(fake.files, listOf(sampleTarget("02.flac")), style)
        assertEquals(listOf(sampleTarget("02.flac")), second.targets)
    }

    @Test
    fun aLibraryTrackIsAFileAndACueSheetsIsNot() {
        val target = TagTarget.of("file:///storage/emulated/0/Music/Mo%C4%9Follar/01%20Garip.flac", "Garip", "Moğollar")
        assertEquals("01 Garip.flac", target?.name)
        assertEquals("flac", target?.extension)
        assertNull(TagTarget.of("http://example.com/stream.mp3"))
        // a CUE sheet's track: its tags are the sheet's
        assertNull(TagTarget.of("file:///storage/emulated/0/Music/Moğollar/album.flac#cue=3"))
    }

    @Test
    fun aTagsNameIsWrittenAsTagLibNamesThem() {
        assertEquals("MY_MOOD", normalizeTagKey(" my mood "))
        assertEquals("REPLAYGAIN_TRACK_GAIN", normalizeTagKey("replaygain_track_gain"))
        assertEquals("", normalizeTagKey("  "))
    }
}
