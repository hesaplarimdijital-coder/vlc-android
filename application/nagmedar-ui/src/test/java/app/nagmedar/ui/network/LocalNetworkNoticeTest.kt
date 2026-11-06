package app.nagmedar.ui.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.network.LocalNetwork
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.tools.Notices

/**
 * Android 17: a media on the local network opened without the local network permission (a radio or a feed served at
 * home) is said in a notice, once a host, with the way to give the permission.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr")
class LocalNetworkNoticeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val host = Any()

    @After
    fun tearDown() {
        Notices.hostStopped(host)
        Notices.shown.value.forEach { Notices.dismiss(it.id) }
    }

    @Test
    fun aHomeRadioWithoutThePermissionIsSaidOnce() {
        Notices.hostStarted(host)
        LocalNetworkAccess.install(context)
        val blocked = LocalNetwork.onBlocked
        assertNotNull(blocked)
        blocked!!("192.168.1.31")
        val notice = Notices.shown.value.single()
        assertEquals("192.168.1.31 yerel ağınızda ve Nağmedar'ın oraya ulaşma izni yok: yerel ağ iznini verin.", notice.text)
        assertEquals("İzin ver", notice.actionLabel)
        assertNotNull(notice.action)
        // the same host again: said once
        blocked("192.168.1.31")
        assertEquals(1, Notices.shown.value.size)
    }
}
