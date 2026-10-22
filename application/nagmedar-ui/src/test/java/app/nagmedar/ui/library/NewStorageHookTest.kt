package app.nagmedar.ui.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.util.NewStorageHandler
import org.videolan.vlc.util.NewStorageHook

/**
 * A drive mounted while the app starts (its broadcast can start the app) is kept until the new
 * interface's handler is installed, then said by it: VLC's "scan it?" window never opens for it.
 */
@RunWith(AndroidJUnit4::class)
class NewStorageHookTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val said = mutableListOf<String>()
    private val handler = object : NewStorageHandler {
        override fun newStorage(context: Context, path: String) {
            said += path
        }
    }

    @After
    fun noHandlerLeft() {
        NewStorageHook.handler = null
    }

    @Test
    fun aDriveBeforeTheHandlerIsSaidOnceItComes() {
        NewStorageHook.handler = null
        NewStorageHook.newStorage(context, "/storage/1234-5678")
        assertEquals(emptyList<String>(), said)
        NewStorageHook.handler = handler
        assertEquals(listOf("/storage/1234-5678"), said)
        NewStorageHook.newStorage(context, "/storage/ABCD-EF01")
        assertEquals(listOf("/storage/1234-5678", "/storage/ABCD-EF01"), said)
    }

    @Test
    fun aDriveKeptIsSaidOnlyOnce() {
        NewStorageHook.handler = null
        NewStorageHook.newStorage(context, "/storage/1234-5678")
        NewStorageHook.handler = handler
        NewStorageHook.handler = handler
        assertEquals(listOf("/storage/1234-5678"), said)
    }
}
