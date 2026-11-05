package app.nagmedar.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 1.0.3: a picked folder's listing kept between starts is brought up to date by a walk: new,
 * changed and removed files; a folder that could not be read keeps what was under it, and a tree
 * that cannot be reached at all keeps every file until the user takes it away.
 */
class SafListingTest {

    private val root = "primary:Music"

    private fun folder(id: String, parent: String = root, depth: Int = 1) = SafEntry(id, parent, id.substringAfterLast('/'), SafEntry.MIME_FOLDER, 10, null, depth)

    private fun file(id: String, parent: String = root, modified: Long = 100, size: Long = 1_000, depth: Int = 1) =
        SafEntry(id, parent, id.substringAfterLast('/'), "audio/flac", modified, size, depth)

    private val album = folder("$root/Albüm")
    private val one = file("$root/Albüm/01.flac", parent = album.documentId, depth = 2)
    private val two = file("$root/Albüm/02.flac", parent = album.documentId, depth = 2)
    private val single = file("$root/tek.flac")
    private val kept = listOf(album, one, two, single)

    @Test
    fun aWalkThatSeesTheSameFilesChangesNothing() {
        val (entries, change) = SafListing.merge(kept, SafWalk(root, kept, setOf(root, album.documentId)))
        assertFalse(change.any)
        assertEquals(kept.toSet(), entries.toSet())
    }

    @Test
    fun addedChangedAndRemovedFiles() {
        val three = file("$root/Albüm/03.flac", parent = album.documentId, depth = 2)
        val retagged = two.copy(modified = 200, size = 1_200)
        // tek.flac was deleted
        val walk = SafWalk(root, listOf(album, one, retagged, three), setOf(root, album.documentId))
        val (entries, change) = SafListing.merge(kept, walk)
        assertEquals(SafChange(added = 1, changed = 1, removed = 1), change)
        assertEquals(setOf(album, one, retagged, three), entries.toSet())
    }

    @Test
    fun aFolderThatCouldNotBeReadKeepsItsFiles() {
        // the album's folder is listed under the root, but its own listing failed (the provider died while it was read)
        val walk = SafWalk(root, listOf(album, single), setOf(root))
        val (entries, change) = SafListing.merge(kept, walk)
        assertFalse(change.any)
        assertEquals(kept.toSet(), entries.toSet())
    }

    @Test
    fun anUnreachableTreeKeepsEveryFile() {
        // the drive was taken out: not even the root answered
        val (entries, change) = SafListing.merge(kept, SafWalk(root, emptyList(), emptySet()))
        assertFalse(change.any)
        assertEquals(kept, entries)
    }

    @Test
    fun aFolderThatIsGoneTakesItsFiles() {
        val walk = SafWalk(root, listOf(single), setOf(root))
        val (entries, change) = SafListing.merge(kept, walk)
        assertEquals(listOf(single), entries)
        assertEquals(SafChange(added = 0, changed = 0, removed = 3), change)
    }

    @Test
    fun theFirstWalkAddsEverything() {
        val (entries, change) = SafListing.merge(emptyList(), SafWalk(root, kept, setOf(root, album.documentId)))
        assertEquals(SafChange(added = 4, changed = 0, removed = 0), change)
        assertEquals(kept, entries)
    }

    @Test
    fun theKeptFileReadsBackAsWritten() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val bytes = ByteArrayOutputStream().also { SafListingStore.encode(it, tree, kept) }.toByteArray()
        assertEquals(kept, SafListingStore.decode(ByteArrayInputStream(bytes), tree))
        // a listing kept for another tree is not this one's
        assertEquals(null, SafListingStore.decode(ByteArrayInputStream(bytes), "$tree%2FBaşka"))
        assertTrue(kept.first().folder)
    }
}
