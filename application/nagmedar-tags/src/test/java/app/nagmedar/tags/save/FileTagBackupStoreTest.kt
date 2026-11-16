package app.nagmedar.tags.save

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.BackupId
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.PictureType
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** 1.2.0: the backups of the saves, as files: whole tags and pictures back, the newest kept. */
@RunWith(AndroidJUnit4::class)
class FileTagBackupStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { FileTagBackupStore({ folder.root.resolve("tag-backups") }, Dispatchers.Unconfined) }
    private val song = ContentRef("file:///storage/emulated/0/Music/Moğollar/01.flac")
    private val other = ContentRef("content://com.android.externalstorage.documents/document/primary%3A02.mp3")
    private val cover = EmbeddedPicture(byteArrayOf(1, 2, 3), "image/jpeg")
    private val back = EmbeddedPicture(byteArrayOf(9, 8), "image/png", "arka", PictureType.BackCover)
    private val snapshot = TagSnapshot(
        TagMap.of("TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar", "Cahit Berkay"), "REPLAYGAIN_TRACK_GAIN" to listOf("-6.2 dB")),
        listOf(cover, back),
    )

    @Test
    fun aBackupComesBackWhole() = runBlocking {
        val id = store.save(song, snapshot, 1_000)
        val backup = store.load(id)!!
        assertEquals(song, backup.ref)
        assertEquals(snapshot, backup.snapshot)
        assertEquals(1_000L, backup.createdAtMillis)
    }

    @Test
    fun historyIsAFilesNewestFirst() = runBlocking {
        val first = store.save(song, snapshot, 1)
        store.save(other, TagSnapshot.Empty, 2)
        val second = store.save(song, snapshot.copy(pictures = emptyList()), 3)
        assertEquals(listOf(second, first), store.history(song).map { it.id })
        assertEquals(1, store.history(other).size)
    }

    @Test
    fun pruneKeepsTheNewest() = runBlocking {
        val ids = (1..5).map { store.save(song, snapshot, it.toLong()) }
        store.prune(2)
        assertEquals(ids.takeLast(2).reversed(), store.history(song).map { it.id })
        // their pictures went with them
        assertEquals(2 * 3, folder.root.resolve("tag-backups").list()!!.size)
        store.delete(ids.last())
        assertNull(store.load(ids.last()))
        store.clear()
        assertTrue(store.history(song).isEmpty())
        assertEquals(0 to 0L, store.size())
    }

    @Test
    fun aBackupThatDoesNotReadIsNone() = runBlocking {
        val id = store.save(song, snapshot, 1)
        folder.root.resolve("tag-backups/${id.value}.json").writeText("{ not json")
        assertNull(store.load(id))
        assertNull(store.load(BackupId(99)))
    }
}
