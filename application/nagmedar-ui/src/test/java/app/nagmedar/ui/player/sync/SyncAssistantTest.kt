package app.nagmedar.ui.player.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** The assistant over the sync tab's delays, as the player's chrome puts it; the moment now set by the test. */
internal class FakeSyncAssist(private val sync: SyncController) : SyncAssistTarget {
    var now: SyncMark? = null

    override fun mark(): SyncMark? = now

    override fun subtitleDelayMs(): Long {
        sync.refresh()
        return sync.values.subtitleDelayMs
    }

    override fun setSubtitleDelay(ms: Long) = sync.setSubtitleDelay(ms)

    override fun remembered(): Boolean = sync.remembered

    override fun remember(on: Boolean) = sync.remember(on)
}

/** The mockups' moment: the line heard at 1:11:42,180. */
internal const val HEARD_MS = 4_302_180L

/**
 * S-704: the sound and the subtitle marked in either order, their difference (to the
 * millisecond, by the clock, while the film plays on) goes onto the subtitles' delay, which the
 * film then remembers; Ctrl+Shift+K takes it back, the film's memory with it. The card shows each
 * step, the next one lit, and does it on a click.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SyncAssistantTest {

    @get:Rule
    val compose = createComposeRule()

    private val player = FakeSyncTarget()
    private val store = FakeSyncStore()
    private val sync = SyncController(player, PendingNativeSubtitles, store, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0, settleMs = 0)
    private val target = FakeSyncAssist(sync)
    private val assistant = SyncAssistant(target)

    private fun at(filmMs: Long, clockMs: Long, rate: Float = 1f) {
        target.now = SyncMark(filmMs, clockMs, rate)
    }

    @Test
    fun theSubtitleLateByThreeHundredFiftyMillisecondsComesEarlier() {
        sync.onMediaStarted("file:///kuzey.mkv")
        assistant.onMediaStarted("file:///kuzey.mkv")
        at(HEARD_MS, clockMs = 100_000)
        assertEquals(HEARD_MS, assistant.markAudio()?.filmMs)
        // the player tells its time a quarter of a second late: the clock says 350 ms
        at(HEARD_MS + 250, clockMs = 100_350)
        assertEquals(HEARD_MS + 350, assistant.markSubtitle()?.filmMs)
        assertEquals(-350L, assistant.differenceMs)

        val applied = assistant.apply()
        assertEquals(AppliedSync(fromMs = 0, toMs = -350, rememberedBefore = false), applied)
        assertEquals(-350L, player.subtitles)
        // the film remembers it
        assertTrue(sync.remembered)
        assertEquals(-350L, store.files["file:///kuzey.mkv"]?.subtitleDelayMs)
        // the marks start again
        assertNull(assistant.audioMark)
        assertNull(assistant.subtitleMark)

        // taken back: the delay as it was, the film remembering nothing again
        assertEquals(applied, assistant.undo())
        assertEquals(0L, player.subtitles)
        assertFalse(sync.remembered)
        assertNull(store.files["file:///kuzey.mkv"])
        assertNull(assistant.applied)
        assertNull("one step back only", assistant.undo())
    }

    @Test
    fun theSubtitleFirstMakesThemLaterOntoTheDelayAlreadyThere() {
        sync.onMediaStarted("file:///kuzey.mkv")
        // G pressed twice before: the player has −100 ms
        player.subtitles = -100
        at(60_000, clockMs = 5_000)
        assistant.markSubtitle()
        at(60_750, clockMs = 5_800)
        assistant.markAudio()
        assertEquals(800L, assistant.differenceMs)
        assertEquals(700L, assistant.apply()?.toMs)
        assertEquals(700L, player.subtitles)
        assertEquals(-100L, assistant.undo()?.fromMs)
        assertEquals(-100L, player.subtitles)
    }

    @Test
    fun aFilmThatRemembersKeepsRememberingWhatIsTakenBack() {
        sync.onMediaStarted("file:///kuzey.mkv")
        sync.setSubtitleDelay(-50)
        sync.remember(true)
        at(10_000, clockMs = 0)
        assistant.markAudio()
        at(10_250, clockMs = 300)
        assistant.markSubtitle()
        assertEquals(AppliedSync(-50, -350, rememberedBefore = true), assistant.apply())
        assertEquals(-350L, store.files["file:///kuzey.mkv"]?.subtitleDelayMs)
        assistant.undo()
        assertTrue(sync.remembered)
        assertEquals(-50L, store.files["file:///kuzey.mkv"]?.subtitleDelayMs)
    }

    @Test
    fun aPauseOrAJumpInBetweenTakesTheFilmsOwnTimes() {
        at(10_000, clockMs = 0)
        assistant.markAudio()
        // paused at the subtitle: the player's time is exact
        at(10_400, clockMs = 5_000, rate = 0f)
        assistant.markSubtitle()
        assertEquals(-400L, assistant.differenceMs)
        // played on after a pause of four seconds: the clock disagrees, the film's times are taken
        at(10_400, clockMs = 9_000)
        assistant.markSubtitle()
        assertEquals(-400L, assistant.differenceMs)
    }

    @Test
    fun atAnotherSpeedTheClockCountsInTheFilmsTime() {
        at(20_000, clockMs = 1_000, rate = 1.5f)
        assistant.markAudio()
        at(20_500, clockMs = 1_400, rate = 1.5f)
        assistant.markSubtitle()
        assertEquals(-600L, assistant.differenceMs)
    }

    @Test
    fun nothingIsAppliedWithoutBothMarks() {
        sync.onMediaStarted("file:///kuzey.mkv")
        assertNull(assistant.apply())
        at(10_000, clockMs = 0)
        assistant.markAudio()
        assertNull(assistant.apply())
        assertEquals(0L, player.subtitles)
        assertFalse(sync.remembered)
        // without a film, nothing is marked
        target.now = null
        assertNull(assistant.markSubtitle())
        assertNull(assistant.subtitleMark)
        // Ctrl+Shift+K with nothing to take back: the marks go
        assertTrue(assistant.clearMarks())
        assertNull(assistant.audioMark)
        assertFalse(assistant.clearMarks())
    }

    @Test
    fun anotherFilmStartsAfresh() {
        assistant.onMediaStarted("file:///kuzey.mkv")
        at(10_000, clockMs = 0)
        assistant.markAudio()
        at(10_100, clockMs = 200)
        assistant.markSubtitle()
        assistant.apply()
        at(20_000, clockMs = 10_000)
        assistant.markAudio()
        // the same film going on keeps all
        assistant.onMediaStarted("file:///kuzey.mkv")
        assertTrue(assistant.applied != null && assistant.audioMark != null)
        assistant.onMediaStarted("file:///baska.mkv")
        assertNull(assistant.applied)
        assertNull(assistant.audioMark)
    }

    @Test
    fun aFilmsTimeToTheMillisecond() {
        val turkish = Locale.forLanguageTag("tr")
        assertEquals("1:11:42,180", preciseClock(HEARD_MS, turkish))
        assertEquals("1:11:42.180", preciseClock(HEARD_MS, Locale.UK))
        assertEquals("0:05,007", preciseClock(5_007, turkish))
    }

    private var audioMs by mutableStateOf<Long?>(null)
    private var subtitleMs by mutableStateOf<Long?>(null)
    private var applied by mutableStateOf<AppliedSync?>(null)
    private val calls = mutableListOf<String>()

    private fun card() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(320.dp)) {
                    SyncAssistantCard(
                        audioMarkMs = audioMs,
                        subtitleMarkMs = subtitleMs,
                        applied = applied,
                        onMarkAudio = { calls += "audio" },
                        onMarkSubtitle = { calls += "subtitle" },
                        onApply = { calls += "apply" },
                        onUndo = { calls += "undo" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theCardGoesStepByStep() {
        card()
        compose.onNodeWithText("Senkron asistanı").assertExists()
        compose.onNodeWithText("Sesi işaretle").assertExists()
        compose.onNodeWithText("Shift+H").assertExists()
        compose.onNodeWithText("Shift+J").assertExists()
        compose.onNodeWithText("Shift+K").assertExists()
        compose.onNodeWithTag("assist-apply").assertIsNotEnabled()
        compose.onNodeWithTag("assist-audio").performClick()

        audioMs = HEARD_MS
        compose.waitForIdle()
        compose.onNodeWithText("Ses işaretlendi · 1:11:42,180").assertExists()
        compose.onNodeWithTag("assist-subtitle").performClick()

        subtitleMs = HEARD_MS + 350
        compose.waitForIdle()
        compose.onNodeWithText("Altyazı işaretlendi · 1:11:42,530").assertExists()
        compose.onNodeWithText("Farkı uygula · −350 ms").assertExists()
        compose.onNodeWithTag("assist-apply").assertIsEnabled().performClick()

        audioMs = null
        subtitleMs = null
        applied = AppliedSync(0, -350, rememberedBefore = false)
        compose.waitForIdle()
        compose.onNodeWithText("Fark uygulandı · −350 ms").assertExists()
        compose.onNodeWithText("Ctrl+Shift+K").assertExists()
        compose.onNodeWithTag("assist-undo").performClick()
        assertEquals(listOf("audio", "subtitle", "apply", "undo"), calls)
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun inEnglish() {
        audioMs = HEARD_MS
        card()
        compose.onNodeWithText("Sound marked · 1:11:42.180").assertExists()
        compose.onNodeWithText("Mark the subtitle").assertExists()
    }

    @Test
    fun theSubtitlesTabHasTheDelayAndTheAssistant() {
        var delay by mutableStateOf(-350L)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp)) {
                    SubtitleSyncSection(
                        delayMs = delay,
                        remembered = true,
                        audioMarkMs = null,
                        subtitleMarkMs = null,
                        applied = null,
                        onDelay = { delay = it },
                        onRemember = { calls += "remember:$it" },
                        onMarkAudio = {},
                        onMarkSubtitle = {},
                        onApply = {},
                        onUndo = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("GECİKME").assertExists()
        compose.onNodeWithTag("subtitles-delay-value").assertExists()
        compose.onNodeWithText("−350 ms").assertExists()
        compose.onNodeWithText("+50").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("−300 ms").assertExists()
        compose.onNodeWithTag("subtitles-remember").performClick()
        assertEquals(listOf("remember:false"), calls)
        compose.onNodeWithTag("sync-assistant").assertExists()
    }
}
