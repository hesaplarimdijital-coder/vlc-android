package app.nagmedar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-310: a row swiped toward the start by touch goes, a short swipe comes back, a mouse does not
 * swipe, screen readers have the action; a handle dragged by touch or with the mouse moves its
 * row past the rows it crosses.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class TouchGesturesTest {

    @get:Rule
    val compose = createComposeRule()

    private val removed = mutableListOf<String>()

    private fun rows(swipe: Boolean = true) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Touch) {
                Column {
                    listOf("Fener", "Liman Işıkları").forEach { title ->
                        SwipeToRemove("Kaldır", { removed += title }, Modifier.fillMaxWidth().height(64.dp).testTag(title), swipe = swipe) {
                            Box(Modifier.fillMaxSize().background(NagTheme.colors.chrome)) { Text(title) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aSwipeTowardTheStartRemoves() {
        rows()
        compose.onNodeWithTag("Fener").performTouchInput { swipeLeft(startX = right - 10f, endX = left + width * 0.2f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("Fener"), removed)
    }

    @Test
    fun aShortSlowSwipeComesBack() {
        rows()
        compose.onNodeWithTag("Fener").performTouchInput { swipeLeft(startX = right - 10f, endX = right - width * 0.25f, durationMillis = 800) }
        compose.waitForIdle()
        assertTrue(removed.isEmpty())
    }

    @Test
    fun aMouseDoesNotSwipe() {
        rows()
        compose.onNodeWithTag("Fener").performMouseInput {
            moveTo(centerRight - Offset(10f, 0f))
            press()
            repeat(8) { moveBy(Offset(-width * 0.1f, 0f)) }
            release()
        }
        compose.waitForIdle()
        assertTrue(removed.isEmpty())
    }

    @Test
    fun screenReadersHaveTheActionWithoutTheSwipe() {
        rows(swipe = false)
        compose.onNodeWithTag("Liman Işıkları").performTouchInput { swipeLeft(durationMillis = 200) }
        compose.waitForIdle()
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("Fener").customAction("Kaldır")
        assertEquals(listOf("Fener"), removed)
    }

    /** Runs the node's action for screen readers named [label]. */
    private fun SemanticsNodeInteraction.customAction(label: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == label }.action() }
    }

    private val order = mutableStateListOf("A", "B", "C", "D", "E", "F")

    private fun reorderable(input: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                val list = rememberLazyListState()
                val reorder = rememberReorderState(list) { from, to -> order.add(to, order.removeAt(from)) }
                LazyColumn(Modifier.fillMaxWidth().height(400.dp).reorderList(reorder), state = list) {
                    itemsIndexed(order, key = { _, key -> key }) { index, key ->
                        Row(Modifier.fillMaxWidth().height(50.dp).reorderItem(reorder, key, index).background(NagTheme.colors.chrome)) {
                            Text(key, Modifier.weight(1f))
                            Box(Modifier.size(48.dp).reorderHandle(reorder, key).testTag("handle-$key"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aHandleDraggedByTouchMovesItsRow() {
        reorderable(InputMode.Touch)
        // B's middle goes past C's and D's, not E's
        compose.onNodeWithTag("handle-B").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 22f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("A", "C", "D", "B", "E", "F"), order.toList())
    }

    @Test
    fun aHandleDraggedWithTheMouseMovesItsRowUp() {
        reorderable(InputMode.Pointer)
        compose.onNodeWithTag("handle-E").performMouseInput {
            moveTo(center)
            press()
            repeat(5) { moveBy(Offset(0f, -22f)) }
            release()
        }
        compose.waitForIdle()
        assertEquals(listOf("A", "B", "E", "C", "D", "F"), order.toList())
    }
}
