package app.nagmedar.ui.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.NavKind
import app.nagmedar.ui.shell.WidthClass
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

/**
 * Desktop Kit 1.0 on a phone: the mini player that grows into the player (a tap, a drag up, Back),
 * its sideways swipe, the "⋯" tab's sheet of the other pages, and the dialogs' close affordance.
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
    private var equalizer = 0

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
                                mini = { Box(Modifier.fillMaxWidth().height(72.dp)) { Text("Sabaha Kadar") } },
                                full = { Box(Modifier.fillMaxSize().testTag("full-player")) { Text("Ada Karan") } },
                                modifier = Modifier.testTag("mini"),
                                onNext = { skips++ },
                                onPrevious = { backs++ },
                            )
                        }
                    },
                    moreActions = listOf(ActionGroup("Oynatma", listOf(MenuEntry.Item("Ekolayzer…", { equalizer++ })))),
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
    fun theMoreTabOpensTheSheetOfPagesAndActions() {
        phone()
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzer…").assertIsDisplayed()
        compose.onNodeWithText("Kaynaklar").assertIsDisplayed()
        compose.onNodeWithText("Bu cihaz").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzer…").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("Bu cihaz").fetchSemanticsNodes().isNotEmpty())
        // the app's own entries run their action
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzer…").performClick()
        compose.waitForIdle()
        assertEquals(1, equalizer)
        // Back closes the sheet without leaving the page
        compose.onNodeWithText("Daha fazla").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Ekolayzer…").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("Bu cihaz").fetchSemanticsNodes().isNotEmpty())
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
