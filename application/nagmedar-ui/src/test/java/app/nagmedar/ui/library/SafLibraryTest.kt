package app.nagmedar.ui.library

import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.device.FolderProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/**
 * 1.0.1: the music of a picked folder is read through its provider; a provider that fails while
 * its rows are read (it died, the disk went) leaves the folder without music instead of ending
 * the app as the Music opens.
 */
@RunWith(AndroidJUnit4::class)
class SafLibraryTest {

    @Test
    fun aProviderFailingWhileReadGivesNoMusicAndNoCrash() = runBlocking {
        // FolderProvider's rows cannot be read under this authority
        val authority = "app.nagmedar.test.failing"
        Robolectric.buildContentProvider(FolderProvider::class.java).create(authority)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root").toString()
        SafLibrary.forget(tree)
        val media = SafLibrary.media(ApplicationProvider.getApplicationContext(), setOf(tree), MediaWrapper.TYPE_AUDIO)
        assertTrue(media.isEmpty())
        SafLibrary.forget(tree)
    }
}
