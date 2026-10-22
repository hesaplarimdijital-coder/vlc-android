package app.nagmedar.ui.player.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.MediaSettings
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

/** The player's delays, as VLC keeps them; null before there is a player. */
internal class FakeSyncTarget(var audio: Long? = 0, var subtitles: Long? = 0) : SyncTarget {
    override fun audioDelayMs(): Long? = audio

    override fun subtitleDelayMs(): Long? = subtitles

    override fun setAudioDelay(ms: Long) {
        audio = ms
    }

    override fun setSubtitleDelay(ms: Long) {
        subtitles = ms
    }
}

internal class FakeNativeSubtitles(override val supportsRate: Boolean) : NativeSubtitles {
    val factors = mutableListOf<Double>()

    override fun setSubtitleRate(factor: Double) {
        factors += factor
    }
}

internal class FakeSyncStore : SyncStore {
    val files = mutableMapOf<String, SyncValues>()

    override suspend fun load(uri: String): SyncValues? = files[uri]

    override suspend fun save(uri: String, values: SyncValues) {
        files[uri] = values
    }

    override suspend fun forget(uri: String) {
        files.remove(uri)
    }
}

/**
 * S-604: the delays move in 10 and 50 ms steps and are the player's, the keys' changes read back;
 * the subtitles' speed waits for the native side; a film remembers its delays when asked and gets
 * them back over VLC's own; the sync tab does each of these from its controls.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SyncTest {

    @get:Rule
    val compose = createComposeRule()

    private val target = FakeSyncTarget()
    private val store = FakeSyncStore()

    private fun controller(native: NativeSubtitles = PendingNativeSubtitles) =
        SyncController(target, native, store, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0, settleMs = 0)

    @Test
    fun theDelaysAreThePlayers() {
        val sync = controller()
        // VLC put back the film's own delays as it started
        target.audio = 120
        sync.onMediaStarted("file:///kuzey.mkv")
        assertEquals(SyncValues(audioDelayMs = 120), sync.values)
        assertFalse(sync.remembered)
        sync.nudgeAudio(SyncController.STEP_MS)
        sync.nudgeSubtitles(-SyncController.SMALL_STEP_MS)
        assertEquals(170L, target.audio)
        assertEquals(-10L, target.subtitles)
        // J pressed: the player has another delay, read back
        target.audio = 120
        sync.refresh()
        assertEquals(120L, sync.values.audioDelayMs)
        // not remembered: nothing written
        assertTrue(store.files.isEmpty())
        sync.resetAll()
        assertEquals(SyncValues(), sync.values)
        assertEquals(0L, target.audio)
    }

    @Test
    fun aFilmRemembersItsDelaysAndGetsThemBackOverVlcs() {
        val sync = controller()
        sync.onMediaStarted("file:///kuzey.mkv")
        sync.setAudioDelay(120)
        sync.remember(true)
        assertEquals(120L, store.files["file:///kuzey.mkv"]?.audioDelayMs)
        sync.setSubtitleDelay(-350)
        assertEquals(-350L, store.files["file:///kuzey.mkv"]?.subtitleDelayMs)
        // another film, then this one again: VLC puts its own delay back first, ours go over it
        sync.onMediaStarted("file:///baska.mkv")
        assertFalse(sync.remembered)
        target.audio = 40
        sync.onMediaStarted("file:///kuzey.mkv")
        assertTrue(sync.remembered)
        assertEquals(120L, target.audio)
        assertEquals(-350L, target.subtitles)
        // a key moves the delay of a remembered film: remembered too
        target.audio = 170
        sync.refresh()
        assertEquals(170L, store.files["file:///kuzey.mkv"]?.audioDelayMs)
        sync.remember(false)
        assertNull(store.files["file:///kuzey.mkv"])
    }

    @Test
    fun withoutAPlayerNothingIsRead() {
        val sync = controller()
        sync.onMediaStarted("file:///kuzey.mkv")
        sync.setAudioDelay(80)
        target.audio = null
        target.subtitles = null
        sync.refresh()
        assertEquals("a player that went keeps its last delays", 80L, sync.values.audioDelayMs)
    }

    @Test
    fun theSubtitlesSpeedWaitsForTheNativeSide() {
        val pending = controller()
        pending.onMediaStarted("file:///kuzey.mkv")
        pending.setRate(SubtitleRate.PalToFilm)
        assertEquals(SubtitleRate.Same, pending.values.subtitleRate)
        val native = FakeNativeSubtitles(supportsRate = true)
        val sync = controller(native)
        sync.onMediaStarted("file:///kuzey.mkv")
        sync.setRate(SubtitleRate.PalToFilm)
        assertEquals(1.0427, native.factors.last(), 0.0001)
        assertEquals(0.959, SubtitleRate.FilmToPal.factor, 0.0001)
        // a film without its own speed starts at the subtitles' own
        sync.onMediaStarted("file:///baska.mkv")
        assertEquals(1.0, native.factors.last(), 0.0)
        assertEquals(SubtitleRate.Same, sync.values.subtitleRate)
    }

    @Test
    fun theSettingsColumnsReadBack() {
        assertNull(SyncCodec.fromSettings(MediaSettings("file:///kuzey.mkv", effects = "{}")))
        assertEquals(
            SyncValues(120, -350, SubtitleRate.PalToFilm),
            SyncCodec.fromSettings(MediaSettings("file:///kuzey.mkv", audioDelayMs = 120, subtitleDelayMs = -350, subtitleRate = "25>23.976")),
        )
        assertEquals(SyncValues(subtitleDelayMs = 50), SyncCodec.fromSettings(MediaSettings("file:///kuzey.mkv", subtitleDelayMs = 50, subtitleRate = "48")))
    }

    private var values by mutableStateOf(SyncValues(audioDelayMs = 120, subtitleDelayMs = -350))
    private var remembered by mutableStateOf(false)
    private val calls = mutableListOf<String>()

    private fun panel(supportsRate: Boolean) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp).height(885.dp)) {
                    SyncPanel(
                        values = values,
                        remembered = remembered,
                        supportsRate = supportsRate,
                        onAudioDelay = { calls += "audio:$it" },
                        onSubtitleDelay = { calls += "subtitles:$it" },
                        onRate = { calls += "rate:${it.key}" },
                        onResetAll = { calls += "reset" },
                        onRemember = { remembered = it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theTabsControls() {
        panel(supportsRate = true)
        compose.onNodeWithText("+120 ms").assertExists()
        compose.onNodeWithText("−350 ms").assertExists()
        compose.onNodeWithContentDescription("Ses gecikmesi: −50 ms").performClick()
        compose.onNodeWithContentDescription("Ses gecikmesi: +10 ms").performClick()
        compose.onNodeWithContentDescription("Altyazı gecikmesi: +50 ms").performClick()
        compose.onNodeWithContentDescription("Altyazı gecikmesi: sıfırla").performClick()
        compose.onNode(hasText("25→23,976") and hasClickAction()).performClick()
        compose.onNodeWithTag("sync-reset-all").performClick()
        assertEquals(listOf("audio:70", "audio:130", "subtitles:-300", "subtitles:0", "rate:25>23.976", "reset"), calls)
        // the keys that move the delays, from the shortcut table
        compose.onNodeWithText("J").assertExists()
        compose.onNodeWithText("H").assertExists()
        // "Remember for this file": the film's own, said by the delays
        compose.onNodeWithTag("sync-remember").assertIsOff().performClick()
        assertTrue(remembered)
        compose.onAllNodesWithText("bu dosya").assertCountEquals(2)
    }

    @Test
    fun theSpeedIsFadedUntilThePlayerCanDoIt() {
        values = SyncValues()
        panel(supportsRate = false)
        compose.onNodeWithTag("sync-pending").assertExists()
        compose.onNode(hasText("25→23,976") and hasClickAction()).assertIsNotEnabled()
        // at 0 there is nothing to reset
        compose.onNodeWithContentDescription("Ses gecikmesi: sıfırla").assertIsNotEnabled()
        compose.onNodeWithText("1,0000×").assertExists()
    }
}
