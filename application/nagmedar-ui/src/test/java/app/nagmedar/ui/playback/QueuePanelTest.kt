package app.nagmedar.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A queue in a list, for the panel's tests and screenshots. */
class FakeQueue(entries: List<QueueEntry>, current: Int) : QueueActions {
    var entries by mutableStateOf(entries)
    var current by mutableIntStateOf(current)
    val played = mutableListOf<Int>()

    override fun play(index: Int) {
        played += index
    }

    override fun remove(index: Int) {
        entries = entries.toMutableList().apply { removeAt(index) }
        if (index < current) current--
    }

    override fun move(from: Int, to: Int) {
        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
    }

    override fun clearUpcoming() {
        entries = entries.take(current + 1)
    }

    override fun restore(index: Int, removed: List<QueueEntry>) {
        entries = entries.toMutableList().apply { addAll(index, removed) }
    }

    val titles: List<String> get() = entries.map { it.title }

    companion object {
        private fun entry(title: String, artist: String, seconds: Int) = QueueEntry(title, title, artist, seconds * 1000L)

        /** The mockup's album and what follows it. */
        val sample = listOf(
            entry("Sabaha Kadar", "Ada Karan", 258),
            entry("Fener", "Ada Karan", 209),
            entry("Liman Işıkları", "Ada Karan", 362),
            entry("Açılış", "Deniz Yüksel Trio", 161),
            entry("Sis", "Kuzey Ensemble", 254),
        )
    }
}

/**
 * S-310: "Sıradaki" shows what plays and what comes next; a tap plays; by touch a swipe removes and
 * the undo bar brings it back, the handle moves; with a pointer Alt+↑/↓, Delete and the menu do it;
 * "Temizle" takes what comes next; Esc closes; screen readers move and remove.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w411dp-h891dp-mdpi")
class QueuePanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val queue = FakeQueue(FakeQueue.sample, current = 0)
    private val undo = UndoBarState()
    private var closed = false

    private fun panel(input: InputMode) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                QueuePanel(queue.entries, queue.current, queue, onClose = { closed = true }, modifier = Modifier.fillMaxSize(), undo = undo)
            }
        }
        compose.waitForIdle()
    }

    private fun row(title: String) = compose.onNodeWithTag("queue-$title")

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == label }.action() }
        compose.waitForIdle()
    }

    @Test
    fun whatPlaysAndWhatComesNext() {
        panel(InputMode.Touch)
        compose.onNodeWithText("Sıradaki").assertExists()
        // 209 + 362 + 161 + 254 seconds
        compose.onNodeWithText("4 parça · 16 dk").assertExists()
        compose.onNodeWithText("Şimdi çalıyor").assertExists()
        compose.onNode(hasText("Açılış") and hasClickAction()).performClick()
        assertEquals(listOf(3), queue.played)
        compose.onNodeWithText("Sola kaydırma: kaldır · Tutamaçla sürükleme: sırala · Uzun basma: menü").assertExists()
    }

    @Test
    fun aSwipeRemovesAndTheUndoBarBringsItBack() {
        panel(InputMode.Touch)
        row("Liman Işıkları").performTouchInput { swipeLeft(startX = right - 10f, endX = left + width * 0.2f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar", "Fener", "Açılış", "Sis"), queue.titles)
        assertEquals("“Liman Işıkları” sıradan kaldırıldı", undo.messages.last().text)
        compose.runOnIdle { assertTrue(undo.undoLatest()) }
        compose.waitForIdle()
        assertEquals(FakeQueue.sample.map { it.title }, queue.titles)
    }

    @Test
    fun theHandleMovesARow() {
        panel(InputMode.Touch)
        val height = row("Fener").fetchSemanticsNode().size.height.toFloat()
        // Fener's middle goes past Liman Işıkları's and Açılış's
        compose.onNodeWithContentDescription("Fener: sürükleyerek taşı").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, height * 0.23f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Açılış", "Fener", "Sis"), queue.titles)
    }

    @Test
    fun withAPointerTheKeysAndTheMenuDoIt() {
        panel(InputMode.Pointer)
        val fener = compose.onNode(hasText("Fener") and hasClickAction())
        fener.requestFocus()
        fener.performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Fener", "Açılış", "Sis"), queue.titles)
        compose.onNode(hasText("Fener") and hasClickAction()).performKeyInput { pressKey(Key.Delete) }
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Açılış", "Sis"), queue.titles)
        // the right button's menu
        compose.onNode(hasText("Açılış") and hasClickAction()).performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Sıradan kaldır").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Sis"), queue.titles)
    }

    @Test
    fun clearTakesWhatComesNextAndEscCloses() {
        panel(InputMode.Pointer)
        compose.onNode(hasText("Temizle") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar"), queue.titles)
        compose.onNodeWithText("Sırada başka bir şey yok").assertExists()
        assertEquals("4 öğe sıradan kaldırıldı", undo.messages.last().text)
        compose.onNodeWithContentDescription("Sıradakini kapat").apply {
            requestFocus()
            performKeyInput { pressKey(Key.Escape) }
        }
        assertTrue(closed)
    }

    @Test
    fun screenReadersMoveAndRemove() {
        panel(InputMode.Pointer)
        row("Fener").customAction("Aşağı taşı")
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Fener", "Açılış", "Sis"), queue.titles)
        row("Sis").customAction("Kaldır")
        assertEquals(listOf("Sabaha Kadar", "Liman Işıkları", "Fener", "Açılış"), queue.titles)
    }
}
