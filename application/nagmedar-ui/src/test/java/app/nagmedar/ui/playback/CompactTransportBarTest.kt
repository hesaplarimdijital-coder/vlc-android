package app.nagmedar.ui.playback

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
 * 1.0.2: the phone's bar is the tablet's transport bar laid out for a phone. Its seek bar seeks where
 * a finger lets it go, the time following the finger; a sideways swipe on the card skips while one
 * on the seek bar only seeks; it lays out on the user's 384 dp, on 412 dp and on 320 dp with the
 * largest text, shuffle and repeat joining where there is room.
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
    fun theSeekBarSeeksWhereLetGoTheTimeFollowingTheFinger() {
        phone()
        val timeline = compose.onNodeWithTag("progress-timeline")
        timeline.performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
        }
        compose.waitForIdle()
        // three quarters of 4:18 held: the time played says it, and the bubble over the finger (the seek bar's row is one
        // node for a screen reader: its parts are looked at one by one)
        compose.onNode(hasText("3:13") and hasTestTag("progress-bubble").not(), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("progress-bubble", useUnmergedTree = true).assertExists()
        timeline.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, seeks.size)
        assertEquals(0.75f, seeks.single(), 0.01f)
        // a drag along the seek bar is the seek bar's: no skip, the player not opened
        assertEquals(0, skips + backs)
        assertFalse(player.isExpanded)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aSidewaysSwipeOnTheCardSkipsOneOnTheSeekBarDoesNot() {
        phone()
        // across the seek bar, end to end: it seeks
        compose.onNodeWithTag("progress-timeline").performTouchInput { swipe(centerRight - Offset(4f, 0f), centerLeft + Offset(4f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(0, skips)
        assertEquals(1, seeks.size)
        // across the words of the card's first row, towards the start edge: the next track
        compose.onNodeWithTag("mini-player").performTouchInput {
            val y = 28.dp.toPx()
            swipe(Offset(230.dp.toPx(), y), Offset(16.dp.toPx(), y), durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals(1, skips)
        // and back
        compose.onNodeWithTag("mini-player").performTouchInput {
            val y = 28.dp.toPx()
            swipe(Offset(16.dp.toPx(), y), Offset(230.dp.toPx(), y), durationMillis = 400)
        }
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(1, seeks.size)
        assertFalse(player.isExpanded)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aTapOnTheWordsOrADragUpOpensTheFullPlayer() {
        phone()
        compose.onNodeWithTag("mini-player").performTouchInput {
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

    /** Everything inside the card, the words clear of the transport, the seek bar's row under both. */
    private fun assertLaidOut(modes: Boolean) {
        val window = compose.onRoot().getBoundsInRoot()
        val card = bounds("mini-player")
        assertTrue("the card in the window: $card", card.left >= window.left && card.right <= window.right && card.bottom <= window.bottom)
        val title = compose.onNodeWithText("Sabaha Kadar", useUnmergedTree = true).getBoundsInRoot()
        val previous = described("Önceki parça")
        val next = described("Sonraki parça")
        val play = described("Duraklat")
        for ((name, b) in listOf("previous" to previous, "play" to play, "next" to next)) {
            assertTrue("$name inside the card: $b in $card", b.left >= card.left && b.right <= card.right && b.top >= card.top && b.bottom <= card.bottom)
            assertTrue("$name a finger's target: $b", b.right - b.left >= 47.5.dp && b.bottom - b.top >= 47.5.dp)
        }
        assertTrue("the title clear of the transport: $title, $previous", title.right <= previous.left + 0.5.dp)
        assertTrue("the title has room: $title", title.right - title.left >= 24.dp)
        val seek = bounds("mini-player-seek")
        assertTrue("the seek bar's row under the first: $seek, $play", seek.top >= play.bottom - 0.5.dp && seek.bottom <= card.bottom + 0.5.dp)
        compose.onNodeWithText("1:48").assertIsDisplayed()
        compose.onNodeWithText("−2:30").assertIsDisplayed()
        if (modes) {
            compose.onNodeWithContentDescription("Karıştır").assertIsDisplayed()
            compose.onNodeWithContentDescription("Tekrarla").assertIsDisplayed()
        } else {
            compose.onNodeWithContentDescription("Karıştır").assertDoesNotExist()
            compose.onNodeWithContentDescription("Tekrarla").assertDoesNotExist()
        }
    }

    @Test
    @Config(qualifiers = "tr-w320dp-h640dp-xhdpi")
    fun fitsAt320() {
        bar()
        assertLaidOut(modes = false)
    }

    @Test
    @Config(qualifiers = "tr-w320dp-h640dp-xhdpi")
    fun fitsAt320WithTheLargestText() {
        bar(fontScale = 2f)
        assertLaidOut(modes = false)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun fitsAt384() {
        bar()
        assertLaidOut(modes = false)
    }

    @Test
    @Config(qualifiers = "tr-w412dp-h915dp-xxhdpi")
    fun fitsAt412() {
        bar()
        assertLaidOut(modes = false)
    }

    @Test
    @Config(qualifiers = "tr-w540dp-h900dp-xhdpi")
    fun shuffleAndRepeatJoinWhereThereIsRoom() {
        bar()
        assertLaidOut(modes = true)
    }

    @Test
    @Config(qualifiers = "tr-w384dp-h832dp-xxhdpi")
    fun aStationOnTheAirIsLiveWithoutASeekBar() {
        bar(info = samplePlayerInfo.copy(title = "Nağme FM", subtitle = "Şimdi çalıyor", lengthMs = 0L, format = null, live = true))
        compose.onNodeWithTag("bar-live", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Canlı yayın").assertIsDisplayed()
        compose.onNodeWithTag("progress-timeline").assertDoesNotExist()
    }
}
