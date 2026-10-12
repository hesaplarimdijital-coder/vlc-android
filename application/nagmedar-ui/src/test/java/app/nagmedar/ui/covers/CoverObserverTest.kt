package app.nagmedar.ui.covers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.vlc.mediadb.models.Widget
import org.videolan.vlc.widget.utils.WidgetType
import org.videolan.vlc.widget.utils.WidgetUtils

/**
 * S-1009: the covers the playback service shows outside the app (notification, Android Auto,
 * widgets) are the app's kept ones, found by album; and the widget's layout for a phone's and a
 * tablet's sizes.
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

    private fun widget(width: Int, height: Int, type: Int = 0) =
        Widget(1, width, height, 0, type, false, 0, 0, 10, 10, 100, true, true, true)

    @Test
    fun theWidgetsLayoutForPhonesAndTablets() {
        // a phone: 4×1, 2×2, a narrow 2×1
        assertEquals(WidgetType.MINI, WidgetUtils.getWidgetType(widget(320, 96)))
        assertEquals(WidgetType.MICRO, WidgetUtils.getWidgetType(widget(170, 180)))
        assertEquals(WidgetType.PILL, WidgetUtils.getWidgetType(widget(150, 60)))
        // a tablet: 5×2 and 3×3
        assertEquals(WidgetType.MACRO, WidgetUtils.getWidgetType(widget(560, 260)))
        assertEquals(WidgetType.MACRO, WidgetUtils.getWidgetType(widget(330, 330)))
        // the kind chosen in its settings, whatever the size
        assertEquals(WidgetType.PILL, WidgetUtils.getWidgetType(widget(560, 260, type = 1)))
    }
}
