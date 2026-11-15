package app.nagmedar.ui.playback

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.ExpandablePlayer
import app.nagmedar.ui.components.ExpandablePlayerState
import app.nagmedar.ui.components.rememberExpandablePlayerState
import app.nagmedar.ui.samplePlayerInfo
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
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

/**
 * 1.0.2: the phone's bar, the full player growing out of it. 1.1.1 (the design's mini player): a card with the words,
 * play in the ink's circle and next; under them the track's waveform, which seeks where a finger lets it go (TalkBack
 * hears the times); a sideways swipe on the card skips while one on the waveform only seeks; it lays out on the user's
 * 384 dp, on 412 dp and on 320 dp with the largest text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactTransportBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private val seeks = mutableListOf<Float>()
    private var skips = 0
    private var backs = 0

    private val actions = object : PlayerBarActions by NoPlayerActions {
        override fun seekTo(fraction: Float) {
            seeks += fraction
        }
    }

    /** The phone's shell with the bar in its expandable player, as the app has it. */
    private fun phone() {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    player = { kind ->
                        if (kind == NavKind.BottomBar) {
                            ExpandablePlayer(
                                player,
                                mini = { CompactTransportBar(samplePlayerInfo, { 0.42f }, actions, { size -> Artwork(null, size) }) },
                                full = { Box(Modifier.fillMaxSize().testTag("full-player")) { Text("Ada Karan") } },
                                onNext = { skips++ },
                                onPrevious = { backs++ },
                            )
                        }
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun theWaveformSeeksWhereLetGo() {
        phone()
        val wave = compose.onNodeWithTag("mini-player-seek")
        // TalkBack hears the time played and the length: 42 % of 4:18
        wave.assertContentDescriptionEquals("1:48 / 4:18")
        wave.performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
        }
        compose.waitForIdle()
        // nothing seeks while the finger is down
        assertEquals(0, seeks.size)
        wave.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, seeks.size)
        assertEquals(0.75f, seeks.single(), 0.01f)
        // a drag along the waveform is the waveform's: no skip, the player not opened
        assertEquals(0, skips + backs)
        assertFalse(player.isExpanded)
        // TalkBack's slider sets it too
        wave.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(0.5f, seeks.last(), 0.001f)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aSidewaysSwipeOnTheCardSkipsOneOnTheSeekBarDoesNot() {
        phone()
        // across the waveform, end to end: it seeks
        compose.onNodeWithTag("mini-player-seek").performTouchInput { swipe(centerRight - Offset(4f, 0f), centerLeft + Offset(4f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(0, skips)
        assertEquals(1, seeks.size)
        // across the words of the card's first row, towards the start edge: the next track (the card is one node with
        // the expandable player that holds it: its own bounds from the unmerged tree)
        compose.onNodeWithTag("mini-player", useUnmergedTree = true).performTouchInput {
            val y = 28.dp.toPx()
            swipe(Offset(190.dp.toPx(), y), Offset(16.dp.toPx(), y), durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals(1, skips)
        // and back
        compose.onNodeWithTag("mini-player", useUnmergedTree = true).performTouchInput {
            val y = 28.dp.toPx()
            swipe(Offset(16.dp.toPx(), y), Offset(190.dp.toPx(), y), durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(1, seeks.size)
        assertFalse(player.isExpanded)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aDragUpOnTheCardOpensTheFullPlayer() {
        phone()
        compose.onNodeWithTag("mini-player", useUnmergedTree = true).performTouchInput {
            val y = 28.dp.toPx()
            swipe(Offset(120.dp.toPx(), y), Offset(120.dp.toPx(), y - 600.dp.toPx()), durationMillis = 600)
        }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        compose.onNodeWithTag("full-player").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- layout

    /** The bar alone at the window's bottom, its text [fontScale] times as large. */
    private fun bar(fontScale: Float = 1f, info: PlayerBarInfo = samplePlayerInfo) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                            CompactTransportBar(info, { 0.42f }, NoPlayerActions, { size -> Artwork(null, size) })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun described(label: String): DpRect = compose.onNodeWithContentDescription(label).getBoundsInRoot()

    /** Everything inside the card (84 dp, the design's), the words clear of play and next, the waveform's row under them. */
    private fun assertLaidOut() {
        val window = compose.onRoot().getBoundsInRoot()
        val card = bounds("mini-player")
        assertTrue("the card in the window: $card", card.left >= window.left && card.right <= window.right && card.bottom <= window.bottom)
        assertEquals("8 dp from the window's sides", 8f, (card.left - window.left).value, 0.5f)
        val title = compose.onNodeWithText("Sabaha Kadar", useUnmergedTree = true).getBoundsInRoot()
        val next = described("Sonraki parça")
        val play = described("Duraklat")
        for ((name, b) in listOf("play" to play, "next" to next)) {
            assertTrue("$name inside the card: $b in $card", b.left >= card.left && b.right <= card.right && b.top >= card.top && b.bottom <= card.bottom)
            assertTrue("$name a finger's target: $b", b.right - b.left >= 47.5.dp && b.bottom - b.top >= 47.5.dp)
        }
        compose.onNodeWithContentDescription("Önceki parça").assertDoesNotExist()
        assertTrue("the title clear of play: $title, $play", title.right <= play.left + 0.5.dp)
        assertTrue("the title has room: $title", title.right - title.left >= 24.dp)
        val seek = bounds("mini-player-seek")
        assertTrue("the waveform's row under the first: $seek, $play", seek.top >= play.bottom - 0.5.dp && seek.bottom <= card.bottom + 0.5.dp)
    }

    @Test
    @Config(qualifiers = "tr-w320dp-h640dp-xhdpi")
    fun fitsAt320() {
        bar()
        assertLaidOut()
    }

    @Test
    @Config(qualifiers = "tr-w320dp-h640dp-xhdpi")
    fun fitsAt320WithTheLargestText() {
        bar(fontScale = 2f)
        assertLaidOut()
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun fitsAt384() {
        bar()
        assertLaidOut()
        // the design's card: 84 dp
        assertEquals(84f, bounds("mini-player").let { it.bottom - it.top }.value, 0.5f)
    }

    @Test
    @Config(qualifiers = "tr-w412dp-h915dp-xxhdpi")
    fun fitsAt412() {
        bar()
        assertLaidOut()
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aStationOnTheAirIsLiveWithoutASeekBar() {
        bar(info = samplePlayerInfo.copy(title = "Nağme FM", subtitle = "Şimdi çalıyor", lengthMs = 0L, format = null, live = true))
        compose.onNodeWithTag("bar-live", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Canlı yayın").assertIsDisplayed()
        compose.onNodeWithTag("progress-timeline").assertDoesNotExist()
        // the live line in the waveform's place, as high: the card keeps its 84 dp
        assertEquals(84f, bounds("mini-player").let { it.bottom - it.top }.value, 0.5f)
    }
}
