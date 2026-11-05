package app.nagmedar.ui.widget

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Android 17: the widget's cover stays within the bitmap memory the system allows an update (6 ×
 * the screen's pixels, every layout's pictures together): its centred square at its place's size,
 * at 2 bytes a pixel when it is opaque. The bitmaps are drawn for real (the native graphics).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetCoversTest {

    @Test
    fun theBudgetIsSixBytesAScreenPixel() {
        // the Galaxy S24 at its default FHD+ (1080 × 2340) and the Tab S11 Ultra (2960 × 1848)
        assertEquals(15_163_200L, WidgetCovers.budget(1080, 2340))
        assertEquals(32_820_480L, WidgetCovers.budget(2960, 1848))
        assertEquals(WidgetCovers.FALLBACK_BYTES, WidgetCovers.budget(0, 2340))
    }

    @Test
    fun theCoverIsDrawnAtItsPlacesSize() {
        assertEquals(96, WidgetCovers.sidePx(1f))
        // the S24's FHD+ density (2.8125, 450 dpi) and its QHD+ (3.75)
        assertEquals(270, WidgetCovers.sidePx(2.8125f))
        assertEquals(360, WidgetCovers.sidePx(3.75f))
        assertEquals(96, WidgetCovers.sidePx(0.5f))
    }

    @Test
    fun aWideCoverIsCutToItsCentredSquare() {
        assertEquals(CoverFit(left = 250, top = 0, side = 1000, target = 270), WidgetCovers.fit(1500, 1000, 270))
        assertEquals(CoverFit(left = 0, top = 50, side = 200, target = 200), WidgetCovers.fit(200, 300, 270))
        assertNull(WidgetCovers.fit(0, 300, 270))
    }

    @Test
    fun theLargestCoverStaysFarBelowAnyScreensBudget() {
        // a QHD+ phone's cover (360 px, with transparency) against the smallest budget a 1080p screen gives
        val worst = WidgetCovers.bytes(WidgetCovers.sidePx(3.75f), opaque = false)
        assertEquals(518_400L, worst)
        assertTrue(worst * 10 < WidgetCovers.budget(1080, 1920))
        assertEquals(145_800L, WidgetCovers.bytes(270, opaque = true))
    }

    @Test
    fun anOpaqueCoverIsKeptAtTwoBytesAPixel() {
        val source = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        source.setHasAlpha(false)
        val cover = WidgetCovers.fitted(source, 270)
        assertNotNull(cover)
        assertEquals(270, cover!!.width)
        assertEquals(270, cover.height)
        assertEquals(Bitmap.Config.RGB_565, cover.config)
        assertTrue(cover.allocationByteCount <= WidgetCovers.bytes(270, opaque = true))
    }

    @Test
    fun aSmallCoverIsNotDrawnLarger() {
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val cover = WidgetCovers.fitted(source, 270)
        assertEquals(120, cover?.width)
        assertEquals(Bitmap.Config.ARGB_8888, cover?.config)
    }
}
