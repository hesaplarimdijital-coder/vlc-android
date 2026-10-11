package app.nagmedar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.StatesBoard
import app.nagmedar.ui.stateCards
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
 * S-308: every state shows the next step and is announced to screen readers; the undo bar stays
 * eight seconds, waits while the pointer rests on it, and Ctrl+Z takes back the newest change.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatesTest {

    @get:Rule
    val compose = createComposeRule()

    private val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: ""

    private fun renderBoard(input: InputMode = InputMode.Pointer) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) { StatesBoard() }
        }
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "tr-w1480dp-h925dp-mdpi")
    fun everyStateNamesTheNextStepAndIsAnnounced() {
        renderBoard()
        val missing = mutableListOf<String>()
        for (card in stateCards) {
            val inCard = hasAnyAncestor(hasTestTag("state-${card.kind}"))
            val steps = compose.onAllNodes(inCard and hasClickAction()).fetchSemanticsNodes().size +
                compose.onAllNodes(inCard and progress).fetchSemanticsNodes().size
            if (steps == 0) missing += "${card.kind}: no next step"
            if (compose.onAllNodes(inCard and liveRegion).fetchSemanticsNodes().isEmpty()) missing += "${card.kind}: not announced"
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test @Config(qualifiers = "tr-w1480dp-h925dp-mdpi")
    fun boardTargetsAreLargeEnoughAndNamed() {
        renderBoard()
        checkTargets(24.dp)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun touchStateTargets() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Touch) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StatePanel(
                        tone = StateTone.Info,
                        icon = NagIcons.Shield,
                        title = "Medya dosyalarına erişim",
                        description = "Tarama yalnızca cihazda yapılır; hiçbir dosya yüklenmez.",
                        primary = StateAction("İzin ver") {},
                        secondary = StateAction("Yalnızca klasör seç") {},
                    )
                }
            }
        }
        compose.waitForIdle()
        checkTargets(48.dp)
    }

    private fun checkTargets(min: Dp) {
        val density = compose.density
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("no clickables", nodes.isNotEmpty())
        val small = nodes.filter { node ->
            with(density) { node.size.width.toDp() < min - 0.5.dp || node.size.height.toDp() < min - 0.5.dp }
        }
        assertTrue(small.joinToString("\n") { node -> with(density) { "'${node.label()}' ${node.size.width.toDp()} x ${node.size.height.toDp()}" } },
            small.isEmpty())
        val unnamed = nodes.filter { it.label().isBlank() }
        assertTrue("${unnamed.size} clickables without a name", unnamed.isEmpty())
    }

    private fun renderUndo(state: UndoBarState) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.BottomCenter) { UndoBarHost(state) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun undoBarLeavesAfterEightSeconds() {
        val state = UndoBarState()
        renderUndo(state)
        compose.runOnUiThread { state.show("3 parça sıradan kaldırıldı", "Geri al", shortcut = "Ctrl+Z") {} }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("3 parça sıradan kaldırıldı").assertExists()
        compose.mainClock.advanceTimeBy(UndoBarState.VISIBLE_MILLIS - 500)
        compose.onNodeWithText("3 parça sıradan kaldırıldı").assertExists()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("3 parça sıradan kaldırıldı").assertDoesNotExist()
    }

    @Test
    fun undoBarWaitsWhileThePointerRestsOnIt() {
        val state = UndoBarState()
        renderUndo(state)
        compose.runOnUiThread { state.show("Gecikme −350 ms olarak kaydedildi", "Geri al") {} }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Gecikme −350 ms olarak kaydedildi").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(12_000)
        compose.onNodeWithText("Gecikme −350 ms olarak kaydedildi").assertExists()
        // the bar sits 40 dp away from the window's corner
        compose.onRoot().performMouseInput { moveTo(Offset(1f, 1f)) }
        compose.mainClock.advanceTimeBy(UndoBarState.VISIBLE_MILLIS + 300)
        compose.onNodeWithText("Gecikme −350 ms olarak kaydedildi").assertDoesNotExist()
    }

    @Test
    fun undoBarIsALiveRegion() {
        val state = UndoBarState()
        renderUndo(state)
        compose.runOnUiThread { state.show("3 parça sıradan kaldırıldı", "Geri al") {} }
        compose.mainClock.advanceTimeByFrame()
        val announced = compose.onAllNodes(liveRegion).fetchSemanticsNodes()
        assertEquals(1, announced.size)
    }

    @Test
    fun undoLatestTakesBackTheNewest() {
        val state = UndoBarState()
        var undone = ""
        state.show("a", "Geri al") { undone += "a" }
        state.show("b", "Geri al") { undone += "b" }
        assertTrue(state.undoLatest())
        assertEquals("b", undone)
        assertTrue(state.undoLatest())
        assertEquals("ba", undone)
        assertFalse(state.undoLatest())
    }

    @Test
    fun atMostTwoBarsStay() {
        val state = UndoBarState()
        repeat(3) { index -> state.show("change $index", "Geri al") {} }
        assertEquals(listOf("change 1", "change 2"), state.messages.map { it.text })
    }
}
