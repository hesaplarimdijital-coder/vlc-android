package app.nagmedar.ui.device

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * This device's picked folders, read through their document provider: a provider that fails while
 * its rows are read leaves the folder unreadable (the page says so) instead of ending the app.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSourceTest {

    private fun folder(authority: String): DeviceLocation.Document {
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root").toString()
        return DeviceLocation.Document(tree, "root")
    }

    private fun source() = AndroidDeviceSource(ApplicationProvider.getApplicationContext()) { emptySet() }

    @Test
    fun aProviderFailingWhileReadIsAnUnreadableFolder() = runBlocking {
        Robolectric.buildContentProvider(FolderProvider::class.java).create(FAILING)
        assertNull(source().list(folder(FAILING)))
    }

    @Test
    fun aFolderIsReadThroughItsProvider() = runBlocking {
        Robolectric.buildContentProvider(FolderProvider::class.java).create(READABLE)
        val entries = source().list(folder(READABLE)).orEmpty()
        assertEquals(listOf("Belgeseller", "yol.mkv"), entries.map { it.name })
        assertEquals(listOf(true, false), entries.map { it.folder })
    }
}

private const val FAILING = "app.nagmedar.test.failing"
private const val READABLE = "app.nagmedar.test.readable"

/** A folder with a subfolder and a film; under [FAILING] its rows cannot be read (the provider died). */
class FolderProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val rows = MatrixCursor(columns).apply {
            addRow(arrayOf<Any?>("root/docs", "Belgeseller", DocumentsContract.Document.MIME_TYPE_DIR, 1_760_000_000_000L, null))
            addRow(arrayOf<Any?>("root/yol.mkv", "yol.mkv", "video/x-matroska", 1_760_000_000_000L, 734_003_200L))
        }
        if (uri.authority != FAILING) return rows
        return object : CursorWrapper(rows) {
            override fun moveToNext(): Boolean = throw IllegalStateException("the provider died")
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
