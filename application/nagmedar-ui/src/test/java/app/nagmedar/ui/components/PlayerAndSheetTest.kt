package app.nagmedar.ui.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NoPlayerActions
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.music.NowPlayingInfo
import app.nagmedar.ui.music.PhonePlayer
import app.nagmedar.ui.shell.LocalShellOverlayHost
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.ShellOverlayHost
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.LocalMotionTier
import app.nagmedar.ui.theme.MotionTier
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
 * Desktop Kit 1.0 on a phone: the mini player that grows into the player (a tap, a drag up, Back),
 * its sideways swipe, the sheets, and the dialogs' close affordance (the "⋯" tab's page: PhoneNavigationTest).
 * 1.0's review: the full player composed once and hidden while the strip rests, a drag down on it
 * (its artwork, its lyrics at their top) closing it as one gesture, and closed sheets that are
 * nothing at all.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-hdpi")
class PlayerAndSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: ExpandablePlayerState
    private var skips = 0
    private var backs = 0

    /** Back that nothing in the app took. */
    private var unhandledBacks = 0

    private val sampleFull: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("full-player")) { Text("Ada Karan") } }

    private fun phone(full: @Composable () -> Unit = sampleFull) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                // the first handler: it gets Back only when no later one is there to
                BackHandler { unhandledBacks++ }
                player = rememberExpandablePlayerState()
                TestShell(
                    WidthClass.Compact,
                    player = { kind ->
                        if (kind == NavKind.BottomBar) {
                            ExpandablePlayer(
                                player,
                                mini = { Box(Modifier.fillMaxWidth().height(72.dp)) { Text("Sabaha Kadar") } },
                                full = full,
                                modifier = Modifier.testTag("mini"),
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
    fun aTapExpandsAndBackCollapses() {
        phone()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        assertEquals(1f, player.progress)
        compose.onNodeWithTag("full-player").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        compose.onNodeWithTag("full-player").assertDoesNotExist()
    }

    @Test
    fun aDragSettlesOnTheNearerSide() {
        phone()
        val root = compose.onRoot().getBoundsInRoot()
        val height = (root.bottom - root.top).value
        // a short, slow drag up: back to the mini player
        compose.onNodeWithTag("mini").performTouchInput {
            swipe(center, center - Offset(0f, height * 0.15f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        // past half way: the full player
        compose.onNodeWithTag("mini").performTouchInput {
            swipe(center, center - Offset(0f, height * 0.7f * density), durationMillis = 1500)
        }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
    }

    @Test
    fun aSidewaysSwipeSkipsPastTheThreshold() {
        phone()
        compose.onNodeWithTag("mini").performTouchInput { swipe(center, center - Offset(30f, 0f), durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(0, skips)
        compose.onNodeWithTag("mini").performTouchInput { swipe(centerRight - Offset(20f, 0f), centerLeft + Offset(20f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, skips)
        compose.onNodeWithTag("mini").performTouchInput { swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, backs)
        assertFalse(player.isExpanded)
    }

    @Test
    fun theFullPlayerIsComposedOnceAndHiddenWhileTheStripRests() {
        var made = 0
        phone(full = {
            remember { made++ }
            sampleFull()
        })
        // composed with the strip, before any expand, and neither read out nor in the way of a touch
        assertEquals(1, made)
        compose.onNodeWithTag("full-player").assertDoesNotExist()
        // another tab and Back from it: the player stays as it is
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("more-page").assertDoesNotExist()
        // opened, closed and opened again: the same composition
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("full-player").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        assertEquals(1, made)
    }

    @Test
    fun aDragDownOnTheArtworkClosesThePlayerInOneGesture() {
        var closes = 0
        phone(full = {
            PhonePlayer(
                info = NowPlayingInfo(title = "Sabaha Kadar", artist = "Ada Karan", album = "Gece Mavisi", format = null, lengthMs = 258_000, playing = true),
                progress = { 0.42f },
                waveform = null,
                actions = NoPlayerActions,
                onClose = { closes++ },
                artwork = { size -> Box(Modifier.size(size)) },
            )
        })
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        // the sheet itself follows the finger and settles with its speed: no drop of the artwork alone, no close after it
        compose.onNodeWithTag("phone-artwork").performTouchInput { swipe(center, center + Offset(0f, 300f * density), durationMillis = 150) }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
        assertEquals(0, closes)
        // collapsed, the player waits composed: its own Back handler is not there to take Back from the window
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, closes)
        assertEquals(1, unhandledBacks)
    }

    @Test
    fun pullingTheLyricsDownAtTheirTopClosesThePlayer() {
        phone(full = {
            LazyColumn(Modifier.fillMaxSize().testTag("lyrics")) { items(60) { Text("Satır $it", Modifier.fillMaxWidth().height(40.dp)) } }
        })
        compose.onNodeWithTag("mini").performClick()
        compose.waitForIdle()
        // scrolled down a little: a pull scrolls the lines back first, the sheet stays
        compose.onNodeWithTag("lyrics").performTouchInput { swipeUp(startY = centerY, endY = centerY - 200f * density, durationMillis = 600) }
        compose.waitForIdle()
        assertTrue(player.isExpanded)
        // at their top a pull down moves the sheet, and the release closes it
        compose.onNodeWithTag("lyrics").performTouchInput { swipeDown(startY = top + 10f, endY = top + 10f + 600f * density, durationMillis = 300) }
        compose.waitForIdle()
        assertFalse(player.isExpanded)
    }

    @Test
    fun aClosedSheetTakesNoOverlayNorAFrame() {
        val host = ShellOverlayHost()
        var open by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                // a touch tablet's timed tier: the old sheet ran its 200 ms close on every row as it composed
                CompositionLocalProvider(LocalShellOverlayHost provides host, LocalMotionTier provides MotionTier.Timed) {
                    Column {
                        repeat(12) { index ->
                            MoreButton(groups = { listOf(ActionGroup(null, listOf(MenuEntry.Item("Sıradaki", {})))) }, title = "Parça $index")
                        }
                        GlassBottomSheet(visible = open, onDismiss = { open = false }, label = "Menü") { Text("Sıradaki") }
                    }
                }
            }
        }
        repeat(15) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(0, host.layers.size)
        }
        compose.onNodeWithContentDescription("Parça 3 için diğer işlemler").assertExists()
        // opened, it takes the overlay; closed and down, it lets it go
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { open = true }
        compose.waitForIdle()
        assertEquals(1, host.layers.size)
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        assertEquals(0, host.layers.size)
    }

    @Test
    fun dismissStyleFollowsTheHardwareKeyboard() {
        fun config(keyboard: Int, hidden: Int) = Configuration().apply {
            this.keyboard = keyboard
            hardKeyboardHidden = hidden
        }
        assertEquals(DismissStyle.EscKey, dismissStyleFor(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertEquals(DismissStyle.CloseButton, dismissStyleFor(config(Configuration.KEYBOARD_12KEY, Configuration.HARDKEYBOARDHIDDEN_NO)))
    }

    @Test
    fun aMenuSplitsIntoTheSheetsGroupsAtItsRules() {
        val a = MenuEntry.Item("Oynat", {})
        val b = MenuEntry.Item("Sıraya ekle", {})
        val c = MenuEntry.Item("Paylaş…", {})
        val groups = listOf(a, b, MenuEntry.Separator, MenuEntry.Separator, c).toActionGroups()
        assertEquals(listOf(listOf(a, b), listOf(c)), groups.map { it.actions })
        assertEquals(listOf(a, b, MenuEntry.Separator, c), groups.toMenuEntries())
    }
}
