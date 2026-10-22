package app.nagmedar.ui.covers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1009: the covers the playback service shows outside the app (notification, Android Auto,
 * widgets) are the app's kept ones, found by album.
 */
@RunWith(AndroidJUnit4::class)
class CoverObserverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(20) { 1 }
    private val track = CoverTrack("file:///sdcard/Music/Cemre Kaya/Gece Yolcusu/01.flac", "Gece Yolcusu", "Cemre Kaya")

    @Test
    fun theKeptCoverByAlbum() {
        val observer = CoverObserver().apply { use(context) }
        assertNull(observer.coverFor(track.key, now = 0))
        runBlocking { CoverCenter.finder(context).choose(track, png) }
        // "none" is believed for a minute, then the cover chosen meanwhile is told
        assertNull(observer.coverFor(track.key, now = 30_000))
        val cover = observer.coverFor(track.key, now = 61_000)!!
        assertEquals(png.toList(), cover.readBytes().toList())
        // an album of Android Auto's list: the same cover as its tracks'
        assertEquals(cover, observer.albumCoverFile("CEMRE KAYA", "Gece Yolcusu "))
        assertNull(observer.albumCoverFile("Cemre Kaya", null))
        // another album's track has none
        assertNull(observer.coverFor(CoverTrack("file:///x.flac", "Konuk", "Deniz Su").key, now = 61_000))
    }

    @Test
    fun withoutTheServiceNothingIsTold() {
        assertNull(CoverObserver().coverFor(track.key))
    }
}
