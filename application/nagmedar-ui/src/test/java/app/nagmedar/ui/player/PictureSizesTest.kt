package app.nagmedar.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.libvlc.MediaPlayer
import java.util.Locale

/** The picture's ratios as the language writes their decimals: the touch sheet, the A key's OSD and the effects tab alike. */
@RunWith(AndroidJUnit4::class)
class PictureSizesTest {

    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun aRatioTakesTheLanguagesDecimalSeparator() {
        val turkish = Locale.forLanguageTag("tr")
        assertEquals("2,39:1", ratioLabel(2.39, 1, turkish))
        assertEquals("2.39:1", ratioLabel(2.39, 1, Locale.ENGLISH))
        assertEquals("16:9", ratioLabel(16.0, 9, turkish))
        assertEquals("2:1", ratioLabel(2.0, 1, turkish))
    }

    @Test
    @Config(qualifiers = "tr")
    fun thePictureSizesInTurkish() {
        assertEquals("2,35:1", pictureSizeLabel(resources, MediaPlayer.ScaleType.SURFACE_235_1))
        assertEquals("2,21:1", pictureSizeLabel(resources, MediaPlayer.ScaleType.SURFACE_221_1))
        assertEquals("16:10", pictureSizeLabel(resources, MediaPlayer.ScaleType.SURFACE_16_10))
    }

    @Test
    @Config(qualifiers = "en")
    fun thePictureSizesInEnglish() {
        assertEquals("2.39:1", pictureSizeLabel(resources, MediaPlayer.ScaleType.SURFACE_239_1))
        assertEquals("4:3", pictureSizeLabel(resources, MediaPlayer.ScaleType.SURFACE_4_3))
    }
}
