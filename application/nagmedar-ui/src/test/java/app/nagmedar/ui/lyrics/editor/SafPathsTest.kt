package app.nagmedar.ui.lyrics.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** S-1005: a local folder in the document ids of a tree the user picked. */
@RunWith(AndroidJUnit4::class)
class SafPathsTest {
    private val roots = mapOf("primary" to "/storage/emulated/0", "1A2B-3C4D" to "/storage/1A2B-3C4D")

    @Test
    fun aFolderInsideTheTreeHasItsDocumentId() {
        assertEquals("primary:Music/Albüm", SafPaths.documentFor("primary:Music", "/storage/emulated/0/Music/Albüm", roots))
        assertEquals("primary:Music", SafPaths.documentFor("primary:Music", "/storage/emulated/0/Music", roots))
        assertEquals("1A2B-3C4D:Müzik", SafPaths.documentFor("1A2B-3C4D:", "/storage/1A2B-3C4D/Müzik", roots))
        assertEquals("primary:", SafPaths.documentFor("primary:", "/storage/emulated/0", roots))
    }

    @Test
    fun aFolderOutsideItHasNone() {
        assertNull(SafPaths.documentFor("primary:Music", "/storage/emulated/0/Movies", roots))
        assertNull(SafPaths.documentFor("primary:Music", "/storage/emulated/0/MusicBox", roots))
        assertNull(SafPaths.documentFor("primary:Music", "/storage/1A2B-3C4D/Music", roots))
        assertNull(SafPaths.documentFor("primary:Music", "/data/local", roots))
    }
}
