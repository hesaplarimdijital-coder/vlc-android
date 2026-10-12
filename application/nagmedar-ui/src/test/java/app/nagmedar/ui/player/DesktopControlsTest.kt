package app.nagmedar.ui.player

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the controls asked the player to do. */
internal class FakePlayerActions : PlayerChromeActions {
    val calls = mutableListOf<String>()

    override fun togglePlayPause() {
        calls += "playPause"
    }

    override fun previous() {
        calls += "previous"
    }

    override fun next() {
        calls += "next"
    }

    override fun seekTo(ms: Long) {
        calls += "seekTo:$ms"
    }

    override fun seekBy(deltaMs: Int) {
        calls += "seekBy:$deltaMs"
    }

    override fun setRate(rate: Float) {
        calls += "rate:$rate"
    }

    override fun setAudioTrack(id: String) {
        calls += "audio:$id"
    }

    override fun setSubtitleTrack(id: String) {
        calls += "subtitles:$id"
    }

    override fun abRepeat() {
        calls += "ab"
    }

    override fun setVolume(percent: Int) {
        calls += "volume:$percent"
    }

    override fun toggleMute() {
        calls += "mute"
    }

    override fun toggleFullscreen() {
        calls += "fullscreen"
    }

    override fun toggleEffects() {
        calls += "effects"
    }

    override fun takeSnapshot() {
        calls += "snapshot"
    }

    override fun setSnapshotSettings(settings: SnapshotSettings) {
        calls += "snapshotSettings:${settings.format},${settings.subtitles},${settings.hdr},${settings.folder}"
    }

    override fun chooseSnapshotFolder() {
        calls += "snapshotFolder"
    }

    override fun pictureInPicture() {
        calls += "pip"
    }

    override fun addSubtitleFile() {
        calls += "add subtitles"
    }

    override fun chooseSubtitleEncoding() {
        calls += "encoding"
    }

    override fun close() {
        calls += "close"
    }
}

/** A film playing at 1:11:45 of 2:18:10, with chapters, an A-B loop, two audio tracks and two subtitles. */
internal val samplePlayerState = PlayerChromeState(
    title = "Kuzey Işığı",
    playing = true,
    timeMs = 4_305_000L,
    lengthMs = 8_290_000L,
    chapters = listOf(
        ChapterMark(0L, "Açılış"),
        ChapterMark(1_200_000L, "Kuzey"),
        ChapterMark(4_000_000L, "Fırtına"),
        ChapterMark(7_460_000L, "Şafak"),
    ),
    abStartMs = 4_150_000L,
    abStopMs = 4_420_000L,
    rate = 1f,
    audioTracks = listOf(TrackChoice("1", "English · TrueHD 7.1 Atmos"), TrackChoice("2", "Türkçe · DTS-HD MA 5.1")),
    audioTrack = "1",
    subtitleTracks = listOf(TrackChoice("3", "Türkçe · SRT"), TrackChoice("4", "English · PGS")),
    subtitleTrack = "3",
    volume = 70,
    maxVolume = 200,
    canFullscreen = true,
    hasPrevious = true,
    hasNext = true,
    canPictureInPicture = true,
)

/** Frames for the timeline without a file: a plain one, whatever the time. */
internal class FakePreviews(private val frame: ImageBitmap = ImageBitmap(320, 180)) : FramePreviews {
    val asked = mutableListOf<Long>()

    override suspend fun frameAt(ms: Long): ImageBitmap {
        asked += ms
        return frame
    }

    override fun close() = Unit
}

/**
 * S-601: over the video, a mouse moving shows the controls and they hide two seconds after it
 * stops, unless it rests on them or a menu is open; every control asks the player for its work;
 * the timeline seeks and shows the chapter and a frame under the mouse; a click on the video shows
 * the controls and a double click fills the screen; narrow windows make room; fingers pass through.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class DesktopControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = FakePlayerActions()
    private val visibility = ChromeVisibility(active = true)
    private val previews = FakePreviews()
    private var state by mutableStateOf(samplePlayerState)

    private fun controls() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                DesktopControls(state, actions, visibility, previews)
            }
        }
        compose.waitForIdle()
    }

    private fun shown() = compose.onNodeWithTag("player-controls").let {
        runCatching { it.assertExists() }.isSuccess
    }

    /**
     * Shown with the mouse resting on them: the test's clock, which runs ahead to whatever waits,
     * does not hide them then.
     */
    private fun shownAndHeld() {
        visibility.onMouseMoved()
        visibility.overBars = true
        compose.waitForIdle()
    }

    /**
     * A change the mouse's handler would make, made from the test's thread: the composition hears
     * of it at once, as the clock is the test's and does not run by itself.
     */
    private fun mouse(change: ChromeVisibility.() -> Unit) {
        visibility.change()
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun aMovingMouseShowsThemAndTwoSecondsOfRestHideThem() {
        controls()
        assertFalse("hidden at first", shown())
        compose.mainClock.autoAdvance = false
        mouse { onMouseMoved() }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("shown when the mouse moves", shown())
        compose.mainClock.advanceTimeBy(1_500)
        mouse { onMouseMoved() }
        compose.mainClock.advanceTimeBy(1_500)
        assertTrue("a move starts the two seconds again", shown())
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(visibility.visible.not())
        compose.mainClock.advanceTimeBy(500)
        assertFalse("hidden two seconds after the last move", shown())
    }

    @Test
    fun theyStayWhileTheMouseRestsOnThemOrAMenuIsOpen() {
        controls()
        compose.mainClock.autoAdvance = false
        mouse {
            onMouseMoved()
            overBars = true
        }
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue("the mouse on the bar keeps them", shown())
        // a menu opened from the bar keeps them when the mouse goes onto it
        compose.onNodeWithTag("player-speed").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(visibility.menuOpen)
        mouse { overBars = false }
        compose.mainClock.advanceTimeBy(5_000)
        assertTrue("an open menu keeps them", shown())
        compose.onNode(hasText("1,5×") and hasClickAction()).performClick()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf("rate:1.5"), actions.calls)
        assertFalse("the menu closed", visibility.menuOpen)
        compose.mainClock.advanceTimeBy(2_500)
        assertFalse("then two seconds hide them", shown())
    }

    @Test
    fun everyControlAsksThePlayer() {
        controls()
        shownAndHeld()
        compose.onNodeWithContentDescription("Duraklat").performClick()
        compose.onNodeWithContentDescription("Önceki").performClick()
        compose.onNodeWithContentDescription("Sonraki").performClick()
        compose.onNodeWithContentDescription("10 sn geri").performClick()
        compose.onNodeWithContentDescription("10 sn ileri").performClick()
        compose.onNodeWithTag("player-ab").performClick()
        compose.onNodeWithContentDescription("Ekran görüntüsü al").performClick()
        compose.onNodeWithContentDescription("Sesi kapat").performClick()
        compose.onNodeWithContentDescription("Tam ekran").performClick()
        compose.onNodeWithContentDescription("Efektler ve filtreler").performClick()
        compose.onNodeWithContentDescription("Oynatıcıyı kapat").performClick()
        assertEquals(
            listOf("playPause", "previous", "next", "seekBy:-10000", "seekBy:10000", "ab", "snapshot", "mute", "fullscreen", "effects", "close"),
            actions.calls,
        )
        // what they show
        compose.onNodeWithTag("player-time").assertExists()
        compose.onNodeWithText("1:11:45 / 2:18:10").assertExists()
        compose.onNodeWithText("Kuzey Işığı").assertExists()
        compose.onNodeWithContentDescription("A-B tekrarını kapat").assertExists()
    }

    /** S-609: the small window, from the bar of a wide enough window, where the device has one. */
    @Test
    fun pictureInPictureFromTheBar() {
        controls()
        shownAndHeld()
        compose.onNodeWithContentDescription("Resim içinde resim").performClick()
        assertEquals(listOf("pip"), actions.calls)
        state = state.copy(canPictureInPicture = false)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Resim içinde resim").assertDoesNotExist()
    }

    /** The right button's click, as the mouse makes it. */
    private fun rightClick(tag: String) {
        compose.onNodeWithTag(tag).performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
    }

    /** S-606: the camera takes a screenshot; a right click opens its choices, as Settings has them. */
    @Test
    fun theScreenshotButtonTakesOneAndItsMenuChooses() {
        controls()
        shownAndHeld()
        compose.onNodeWithContentDescription("Ekran görüntüsü al").performClick()
        rightClick("player-snapshot")
        assertTrue("the menu keeps the controls", visibility.menuOpen)
        compose.onNode(hasText("Ekran görüntüsü al") and hasClickAction()).assertExists()
        compose.onNodeWithText("Klasör: Pictures/Nağmedar").assertExists()
        compose.onNode(hasText("Altyazılı") and hasClickAction()).assertIsOn()
        compose.onNode(hasText("WebP") and hasClickAction()).assertIsOff().performClick()
        compose.waitForIdle()
        assertFalse("the menu closed", visibility.menuOpen)
        rightClick("player-snapshot")
        compose.onNode(hasText("Klasörü değiştir…") and hasClickAction()).performClick()
        compose.waitForIdle()
        // a chosen folder can be left for the default one
        state = state.copy(snapshot = SnapshotSettings(folder = "content://com.android.externalstorage.documents/tree/primary%3APictures%2FEkran"))
        rightClick("player-snapshot")
        compose.onNodeWithText("Klasör: Pictures/Ekran").assertExists()
        compose.onNode(hasText("Varsayılan klasöre dön") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(
                "snapshot",
                "snapshotSettings:WebP,true,ToneMap,null",
                "snapshotFolder",
                "snapshotSettings:Png,true,ToneMap,null",
            ),
            actions.calls,
        )
    }

    @Test
    fun tracksAreChosenFromTheirMenus() {
        controls()
        shownAndHeld()
        compose.onNodeWithText("English · TrueHD 7.1 Atmos").assertExists()
        compose.onNodeWithTag("player-audio").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Türkçe · DTS-HD MA 5.1") and hasClickAction()).performClick()
        compose.onNodeWithTag("player-subtitles").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Kapalı") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("audio:2", "subtitles:-1"), actions.calls)
        // without subtitles the button says so
        state = state.copy(subtitleTrack = null)
        compose.waitForIdle()
        compose.onNodeWithText("Kapalı").assertExists()
        // S-701: a file of subtitles, from the system's picker
        compose.onNodeWithTag("player-subtitles").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ctrl+Shift+A").assertExists()
        compose.onNode(hasText("Altyazı dosyası ekle…") and hasClickAction()).performClick()
        compose.waitForIdle()
        // S-702: the character set they are read in
        compose.onNodeWithTag("player-subtitles").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Altyazı kodlaması…") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("audio:2", "subtitles:-1", "add subtitles", "encoding"), actions.calls)
    }

    @Test
    fun theTimelineSeeksAndShowsTheMomentUnderTheMouse() {
        controls()
        shownAndHeld()
        val timeline = compose.onNodeWithTag("player-timeline")
        timeline.performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(listOf("seekTo:${state.lengthMs / 2}"), actions.calls)
        // the mouse at 0.95: 2:11:15, in the chapter "Şafak", with a frame
        timeline.performMouseInput { moveTo(Offset(width * 0.95f, height / 2f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("player-preview").assertExists()
        compose.onNodeWithText("2:11:15 · Bölüm 4 — Şafak").assertExists()
        // the mouse rests a moment before a frame is read
        compose.waitUntil(5_000) { previews.asked.isNotEmpty() }
        timeline.performMouseInput { moveTo(Offset(-50f, -400f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("player-preview").assertDoesNotExist()
    }

    @Test
    fun aClickOnTheVideoShowsThemAndADoubleClickFillsTheScreen() {
        controls()
        compose.mainClock.autoAdvance = false
        val video = compose.onNodeWithTag("player-chrome")
        video.performMouseInput { click(center) }
        // a click waits for the double click's time before it counts as one
        compose.mainClock.advanceTimeBy(400)
        assertTrue(visibility.visible)
        assertEquals(emptyList<String>(), actions.calls)
        video.performMouseInput { doubleClick(Offset(width / 2f, height / 3f)) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(listOf("fullscreen"), actions.calls)
    }

    private var barWidth by mutableStateOf(1280.dp)

    /** From a tablet's narrowest window to a desktop's, beside the panel or not: nothing is squeezed out of the bar. */
    @Test
    fun atEveryWidthTheFullScreenButtonStaysInTheBar() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(barWidth)) { DesktopControls(state, actions, visibility, previews) }
            }
        }
        shownAndHeld()
        val fullscreen = ApplicationProvider.getApplicationContext<Context>().getString(R.string.nag_sc_fullscreen)
        for (width in 600..1280 step 4) {
            compose.runOnIdle { barWidth = width.dp }
            compose.waitForIdle()
            val bar = compose.onNodeWithTag("player-controls").getUnclippedBoundsInRoot()
            val button = compose.onNodeWithContentDescription(fullscreen).getUnclippedBoundsInRoot()
            val buttonWidth = button.right - button.left
            assertTrue("at $width dp the full-screen button is $buttonWidth wide", buttonWidth >= 24.dp)
            assertTrue("at $width dp the full-screen button ends at ${button.right} of ${bar.right}", button.right <= bar.right - 15.dp)
            val volume = runCatching { compose.onNodeWithTag("player-volume").getUnclippedBoundsInRoot() }.getOrNull()
            if (volume != null) {
                val sliderWidth = volume.right - volume.left
                assertTrue("at $width dp the volume's slider is $sliderWidth wide", sliderWidth >= 95.dp)
            }
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun aNarrowWindowMakesRoom() {
        controls()
        shownAndHeld()
        compose.onNodeWithTag("player-speed").assertDoesNotExist()
        compose.onNodeWithTag("player-ab").assertDoesNotExist()
        compose.onNodeWithTag("player-volume").assertDoesNotExist()
        compose.onNodeWithText("1:11:45").assertExists()
        compose.onNodeWithTag("player-audio").assertExists()
        compose.onNodeWithTag("player-subtitles").assertExists()
    }

    @Test
    fun fingersPassThroughToTheClassicOverlay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val frame = MouseOnlyFrame(context, visibility)
        val received = mutableListOf<Int>()
        val child = View(context).apply { setOnTouchListener { _, event -> received += event.getToolType(0); true } }
        frame.addView(child, 100, 100)
        frame.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 100, 100)
        assertFalse("a finger is not the controls'", frame.dispatchTouchEvent(press(MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN)))
        assertTrue("a mouse is", frame.dispatchTouchEvent(press(MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)))
        assertEquals(listOf(MotionEvent.TOOL_TYPE_MOUSE), received)
        // after a finger the controls are not active: the mouse's next press makes them so first
        visibility.onFinger()
        assertFalse(frame.dispatchTouchEvent(press(MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE)))
    }

    private fun press(toolType: Int, source: Int): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 50f; y = 50f })
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, source, 0)
    }
}
