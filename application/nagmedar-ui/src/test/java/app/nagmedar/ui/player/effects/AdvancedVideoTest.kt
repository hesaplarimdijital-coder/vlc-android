package app.nagmedar.ui.player.effects

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-610: the advanced effects as libVLC's options: the filters in their order, the overlays'
 * options, the text escaped for VLC's marq, the ranges kept; where VLC places an overlay; the
 * anaglyph's channels; the session's controller waiting for libVLC to start again.
 */
class AdvancedVideoTest {

    @Test
    fun nothingSetIsNoOption() {
        val options = AdvancedVideoOptions.build(AdvancedVideo())
        assertTrue(options.isEmpty)
        assertTrue(options.options.isEmpty())
        assertFalse(options.cpu)
        assertTrue(AdvancedVideo().isNeutral)
    }

    @Test
    fun anaglyphComesBeforeTheTurnAndRotateAfter() {
        val options = AdvancedVideoOptions.build(AdvancedVideo(angle = 30, anaglyph = true, anaglyphScheme = AnaglyphScheme.MagentaCyan))
        assertEquals(listOf("anaglyph"), options.before)
        assertEquals(listOf("rotate"), options.after)
        assertEquals(listOf("--anaglyph-scheme=magenta-cyan", "--rotate-angle=30"), options.options)
        assertTrue(options.cpu)
    }

    @Test
    fun theAngleIsKeptWithinATurn() {
        assertEquals(270, AdvancedVideoOptions.normalAngle(-90))
        assertEquals(0, AdvancedVideoOptions.normalAngle(360))
        assertEquals(5, AdvancedVideoOptions.normalAngle(725))
        assertTrue(AdvancedVideoOptions.build(AdvancedVideo(angle = 360)).isEmpty)
    }

    @Test
    fun theLogoIsAPictureAtItsPlace() {
        val video = AdvancedVideo(logo = LogoOverlay(enabled = true, file = "/data/cache/overlays/logo-1.png", position = OverlayPosition.BottomRight, opacityPercent = 50))
        val options = AdvancedVideoOptions.build(video, pictureHeight = 2160)
        assertEquals(listOf("logo"), options.subSources)
        assertEquals(
            listOf("--logo-file=/data/cache/overlays/logo-1.png", "--logo-position=10", "--logo-x=65", "--logo-y=65", "--logo-opacity=128"),
            options.options,
        )
        // drawn as a subpicture: the picture stays on the GPU
        assertFalse(options.cpu)
    }

    @Test
    fun aLogoWithoutAPictureOrAPathVlcWouldSplitIsLeftOut() {
        assertTrue(AdvancedVideoOptions.build(AdvancedVideo(logo = LogoOverlay(enabled = true))).isEmpty)
        assertTrue(AdvancedVideoOptions.build(AdvancedVideo(logo = LogoOverlay(enabled = false, file = "/a.png"))).isEmpty)
        assertTrue(AdvancedVideoOptions.build(AdvancedVideo(logo = LogoOverlay(enabled = true, file = "/a,1000,255.png"))).isEmpty)
        assertFalse(AdvancedVideoOptions.logoPathUsable("/x;y.png"))
        assertTrue(AdvancedVideoOptions.logoPathUsable("/sdcard/Nağmedar logo.png"))
    }

    @Test
    fun theTextIsEscapedForVlcsMarq() {
        assertEquals("İndirim %%50 ağustos", AdvancedVideoOptions.marqueeText("  İndirim %50\tağustos ", TextClock.None))
        assertEquals("Canlı %H:%M:%S", AdvancedVideoOptions.marqueeText("Canlı", TextClock.Time))
        assertEquals("%Y-%m-%d %H:%M", AdvancedVideoOptions.marqueeText("   ", TextClock.DateTime))
        assertEquals("a b", AdvancedVideoOptions.marqueeText("a\nb", TextClock.None))
    }

    @Test
    fun theTextsOptions() {
        val video = AdvancedVideo(
            text = TextOverlay(enabled = true, text = "Nağmedar", position = OverlayPosition.Top, size = TextSize.Large, colour = 0xFFE14D, opacityPercent = 100, clock = TextClock.Time),
        )
        val options = AdvancedVideoOptions.build(video, pictureHeight = 1080)
        assertEquals(listOf("marq"), options.subSources)
        assertEquals(
            listOf(
                "--marq-marquee=Nağmedar %H:%M:%S", "--marq-position=4", "--marq-x=0", "--marq-y=32", "--marq-size=86",
                "--marq-color=16769357", "--marq-opacity=255", "--marq-timeout=0", "--marq-refresh=1000",
            ),
            options.options,
        )
        // an empty text without a clock draws nothing
        assertTrue(AdvancedVideoOptions.build(AdvancedVideo(text = TextOverlay(enabled = true, text = " "))).isEmpty)
    }

    @Test
    fun rangesAreKept() {
        assertEquals(0, AdvancedVideoOptions.opacity(-5))
        assertEquals(255, AdvancedVideoOptions.opacity(140))
        assertEquals(8, AdvancedVideoOptions.textPixels(TextSize.Small, 100))
        assertEquals(400, AdvancedVideoOptions.textPixels(TextSize.Large, 8000))
        // an unknown height is a 1080p picture's
        assertEquals(
            AdvancedVideoOptions.build(AdvancedVideo(text = TextOverlay(enabled = true, text = "x")), 1080),
            AdvancedVideoOptions.build(AdvancedVideo(text = TextOverlay(enabled = true, text = "x")), 0),
        )
    }

    @Test
    fun overlaysArePlacedAsVlcPlacesThem() {
        assertEquals(10f to 10f, AdvancedVideoOptions.place(OverlayPosition.TopLeft, 1920f, 1080f, 200f, 100f, 10f))
        assertEquals(1710f to 970f, AdvancedVideoOptions.place(OverlayPosition.BottomRight, 1920f, 1080f, 200f, 100f, 10f))
        assertEquals(860f to 490f, AdvancedVideoOptions.place(OverlayPosition.Center, 1920f, 1080f, 200f, 100f, 10f))
        assertEquals(860f to 10f, AdvancedVideoOptions.place(OverlayPosition.Top, 1920f, 1080f, 200f, 100f, 10f))
        // a centred axis moves by no margin
        assertEquals(0 to 0, AdvancedVideoOptions.offsets(OverlayPosition.Center, 30))
        assertEquals(30 to 0, AdvancedVideoOptions.offsets(OverlayPosition.Right, 30))
        assertEquals(30 to 30, AdvancedVideoOptions.offsets(OverlayPosition.BottomLeft, 30))
    }

    @Test
    fun theAnaglyphsChannelsFollowVlcsSchemes() {
        val (left, right) = AnaglyphScheme.RedCyan.weights()
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), left, 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), right, 0f)
        // blue is in both glasses: each half gives half of it
        val (ml, mr) = AnaglyphScheme.MagentaCyan.weights()
        assertArrayEquals(floatArrayOf(1f, 0f, 0.5f), ml, 0f)
        assertArrayEquals(floatArrayOf(0f, 1f, 0.5f), mr, 0f)
        val (tl, tr) = AnaglyphScheme.Trioscopic.weights()
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), tl, 0f)
        assertArrayEquals(floatArrayOf(1f, 0f, 1f), tr, 0f)
    }

    private class FakeTarget : VideoOptionsTarget {
        val set = mutableListOf<VideoFilterOptions>()
        var running = VideoFilterOptions()

        override fun set(options: VideoFilterOptions) {
            set += options
        }

        override fun started(): VideoFilterOptions = running
    }

    @Test
    fun aChangeWaitsForLibVlcToStartAgain() {
        val target = FakeTarget()
        val controller = AdvancedVideoController(target)
        controller.update { it.copy(angle = 45) }
        assertTrue(controller.waiting)
        assertEquals(listOf("--rotate-angle=45"), target.set.last().options)
        // libVLC started again with them
        target.running = target.set.last()
        controller.refresh()
        assertFalse(controller.waiting)
        // the same again sets nothing
        controller.update { it.copy(angle = 45) }
        assertEquals(1, target.set.size)
        controller.update { it.copy(angle = 0) }
        assertTrue(controller.waiting)
    }

    @Test
    fun aTextIsSizedForThePlayingFilm() {
        val target = FakeTarget()
        val controller = AdvancedVideoController(target)
        controller.update { it.copy(text = TextOverlay(enabled = true, text = "x", size = TextSize.Medium)) }
        assertTrue("--marq-size=54" in target.set.last().options)
        controller.onPicture(2160)
        assertTrue("--marq-size=108" in target.set.last().options)
        assertEquals(2160, controller.pictureHeight)
        // no text: a new height changes nothing libVLC has
        val before = target.set.size
        controller.update { it.copy(text = it.text.copy(enabled = false)) }
        controller.onPicture(720)
        assertEquals(before + 1, target.set.size)
    }

    /**
     * The preview's frame: one read from the file has no effect, so every effect is drawn over it;
     * the screen's has those libVLC shows drawn already, so it is taken only while none shows.
     */
    @Test
    fun thePreviewDrawsTheEffectsOnceOnly() {
        val started = AdvancedVideoOptions.build(AdvancedVideo(angle = 30, logo = LogoOverlay(enabled = true, file = "/l.png")))
        assertTrue(PreviewSource.inForce(started, null))
        assertTrue(PreviewSource.inForce(VideoFilterOptions(), "90"))
        assertFalse(PreviewSource.inForce(VideoFilterOptions(), null))
        assertEquals(PreviewSource.Raw, PreviewSource.choose(raw = true, screen = true, inForce = true))
        // a turned or rotated screen would be turned again
        assertEquals(PreviewSource.Sample, PreviewSource.choose(raw = false, screen = true, inForce = true))
        assertEquals(PreviewSource.Screen, PreviewSource.choose(raw = false, screen = true, inForce = false))
        assertEquals(PreviewSource.Sample, PreviewSource.choose(raw = false, screen = false, inForce = false))
    }
}
