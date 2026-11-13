package app.nagmedar.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.shell.Destination
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
 * S-305: the palette finds commands, screens, settings and media fuzzily and the Turkish way;
 * the arrows and Enter run a hit, Tab and the prefixes narrow the search, Escape closes, and with
 * no query it shows what ran lately. 1.0.7: a kind that shows its best leads to the rest; down a list longer than
 * the palette the selected row is whole; a pointer resting over the list leaves the selection to the arrows.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
class CommandPaletteTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ran = mutableListOf<String>()
    private var open by mutableStateOf(true)

    private fun items(): List<PaletteItem> {
        val res = compose.activity.resources
        return commandItems(res, { it != ShortcutId.AbSwitch }, { ran += "command:${it.name}" }) +
            screenItems(res) { ran += "screen:${it.route}" } +
            listOf(
                PaletteItem("media:kuzey", PaletteKind.Media, "Kuzey Işığı", "Parça · Deniz Aral", icon = NagIcons.Music) { ran += "media:kuzey" },
                PaletteItem("media:sabaha", PaletteKind.Media, "Sabaha Kadar", "Parça · Ada Karan", icon = NagIcons.Music) { ran += "media:sabaha" },
            )
    }

    /** 1.0.7: forty tracks, "Uzun 01" to "Uzun 40": a list several times as long as the palette. */
    private fun long(): List<PaletteItem> = (1..40).map { n ->
        PaletteItem("media:uzun$n", PaletteKind.Media, "Uzun %02d".format(n), "Parça · Deniz Aral", icon = NagIcons.Music) { ran += "media:uzun$n" }
    }

    private fun palette(recent: List<String> = emptyList(), reading: Boolean = false, more: List<PaletteItem> = emptyList()) {
        // while the library is read its media are not among the items
        val items = (items() + more).filter { !reading || it.kind != PaletteKind.Media }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                if (open) {
                    CommandPalette(
                        items = items,
                        recent = recent,
                        onRun = { it.run() },
                        onDismiss = { open = false },
                        reading = reading,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** 1.0.7: the lines under the kinds that show their best only ("14 sonucun hepsini göster"), top to bottom. */
    private fun more(): List<String> = compose.onAllNodesWithTag("palette-more").fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty() }

    private val field get() = compose.onNodeWithContentDescription("Komut, ekran, ayar ya da medya ara")

    private fun label(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()

    /** 1.0.7: the selected row, which the list has to have brought in. */
    private fun selectedRow(): SemanticsNode = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes()
        .also { assertEquals("selected rows: ${it.map(::label)}", 1, it.size) }
        .single()

    private fun listBounds(): Rect = compose.onNodeWithTag("palette-list").fetchSemanticsNode().boundsInRoot

    /** 1.0.7: nothing of the node is cut by the list's edges (a node's bounds are what shows of it). */
    private fun assertWhole(node: SemanticsNode) {
        val name = label(node)
        val list = listBounds()
        assertEquals("$name: what shows of its height", node.size.height.toFloat(), node.boundsInRoot.height, 0.5f)
        assertTrue("$name is above the list's top", node.boundsInRoot.top >= list.top)
        assertTrue("$name is under the list's bottom", node.boundsInRoot.bottom <= list.bottom)
    }

    /** Labels of the rows, top to bottom, and which is selected. */
    private fun rows(): List<Pair<String, Boolean>> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        .fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .map { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty() to (node.config.getOrNull(SemanticsProperties.Selected) == true)
        }

    @Test
    fun typingFindsTurkishWordsFuzzily() {
        palette()
        field.performTextInput("kuzey isigi")
        compose.waitForIdle()
        assertEquals("Kuzey Işığı", rows().first().first)
        compose.onNodeWithText("Eşleşen bir şey yok").assertDoesNotExist()
        field.performTextInput("qqq")
        compose.waitForIdle()
        compose.onNodeWithText("Eşleşen bir şey yok").assertExists()
        assertTrue(rows().isEmpty())
    }

    @Test
    fun theArrowsAndEnterRunAHit() {
        palette()
        field.performTextInput("ses")
        compose.waitForIdle()
        val hits = rows()
        assertTrue(hits.first().second)
        assertTrue(hits.first().first, hits.first().first.startsWith("Ses"))
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertTrue(rows()[1].second)
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(1, ran.size)
        assertFalse(open)
    }

    @Test
    fun aClickRunsAHitAndEscapeCloses() {
        palette()
        field.performTextInput("kuzey")
        compose.waitForIdle()
        compose.onNode(hasText("Kuzey Işığı") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        assertEquals(listOf("media:kuzey"), ran)
        assertFalse(open)

        open = true
        compose.waitForIdle()
        field.performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertFalse(open)
        assertEquals(1, ran.size)
    }

    /** S-1501: in English the groups' titles are English capitals: MEDIA, not MEDİA. */
    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun inEnglishTheTitlesAreEnglishCapitals() {
        palette()
        compose.onNodeWithContentDescription("Search commands, screens, settings or media").performTextInput("kuzey")
        compose.waitForIdle()
        compose.onNodeWithText("MEDIA").assertExists()
        compose.onNodeWithText("MEDİA").assertDoesNotExist()
    }

    @Test
    fun prefixesAndTabNarrowTheSearch() {
        palette()
        field.performTextInput("@sa")
        compose.waitForIdle()
        assertEquals(listOf("Sabaha Kadar"), rows().map { it.first })
        compose.onNodeWithText("Medya", useUnmergedTree = true).assertExists()

        // Tab goes on from Media to all kinds; the typed prefix gives way
        field.performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        assertTrue(rows().size > 1)
        field.performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        // commands only
        assertTrue(rows().all { (label, _) -> label.isNotEmpty() })
        compose.onNodeWithText("Sabaha Kadar").assertDoesNotExist()
    }

    /** 1.0.7: six commands of many were shown and nothing led to the rest but the kinds' chips. */
    @Test
    fun aKindThatShowsItsBestLeadsToTheRest() {
        palette()
        field.performTextInput("a")
        compose.waitForIdle()
        // the commands' six, then the line that says how many there are
        val line = more().first()
        val total = Regex("""^(\d+) sonucun hepsini göster$""").find(line)?.groupValues?.get(1)?.toInt()
        assertTrue(line, total != null && total > 6)
        compose.onAllNodesWithTag("palette-more").fetchSemanticsNodes().minByOrNull { it.boundsInRoot.top }!!
            .let { node -> compose.onNode(SemanticsMatcher("the first line") { it.id == node.id }).performClick() }
        compose.waitForIdle()
        // the commands alone, more than six of them, and no such line: the palette stays open, nothing ran
        assertTrue(more().isEmpty())
        assertTrue("${rows().size} rows", rows().size > 6)
        assertTrue(open)
        assertTrue(ran.isEmpty())
    }

    /** 1.0.7: the arrows stop at that line and Enter on it shows them all. */
    @Test
    fun theArrowsStopAtTheLineAndEnterShowsThemAll() {
        palette()
        field.performTextInput("a")
        compose.waitForIdle()
        assertTrue(more().isNotEmpty())
        // six hits down from the first: the line
        repeat(6) { field.performKeyInput { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        assertTrue("no hit is selected on the line", rows().none { it.second })
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertTrue(open)
        assertTrue(ran.isEmpty())
        assertTrue(more().isEmpty())
        assertTrue(rows().size > 6)
        // the first of them is selected again, and Enter runs it
        assertTrue(rows().first().second)
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(1, ran.size)
        assertTrue(ran.single(), ran.single().startsWith("command:"))
    }

    /**
     * 1.0.7: down a list longer than the palette the selected row is whole and at the list's bottom (until then the cut
     * line under it was the selected one); around the list's ends the first row comes with its title, the last one whole.
     */
    @Test
    fun theSelectedRowIsWholeAllTheWayDownAndAround() {
        palette(more = long())
        field.performTextInput("@uzun")
        compose.waitForIdle()
        assertEquals("Uzun 01", label(selectedRow()))
        for (n in 2..40) {
            field.performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            val row = selectedRow()
            assertEquals("Uzun %02d".format(n), label(row))
            assertWhole(row)
            // once the list has moved the row is its last whole line: the list moved as little as shows it
            if (n >= 20) assertEquals("row $n above the list's padding", 6f, listBounds().bottom - row.boundsInRoot.bottom, 0.5f)
        }
        // down from the last: the first again, with the kind's title above it
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals("Uzun 01", label(selectedRow()))
        assertWhole(selectedRow())
        assertWhole(compose.onNodeWithText("MEDYA").fetchSemanticsNode())
        // up from the first: the last, whole
        field.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals("Uzun 40", label(selectedRow()))
        assertWhole(selectedRow())
        // and up the list the row is whole at each step, at the list's top once the list has moved
        for (n in 39 downTo 20) {
            field.performKeyInput { pressKey(Key.DirectionUp) }
            compose.waitForIdle()
            val row = selectedRow()
            assertEquals("Uzun %02d".format(n), label(row))
            assertWhole(row)
        }
        assertEquals("the row at the list's top, under its padding", 6f, selectedRow().boundsInRoot.top - listBounds().top, 0.5f)
    }

    /**
     * 1.0.7: a pointer resting over the list leaves the selection to the arrows when they scroll the list under it (each
     * line that came under it took the selection back, so the arrows never got past the list's edge); a move of its own
     * and the pointer chooses again.
     */
    @Test
    fun aRestingPointerLeavesTheSelectionToTheArrows() {
        palette(more = long())
        field.performTextInput("@uzun")
        compose.waitForIdle()
        val list = compose.onNodeWithTag("palette-list")
        // the pointer comes to the third row and rests there: it chose the row
        val third = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().single { label(it) == "Uzun 03" }
        val rest = third.boundsInRoot.center - listBounds().topLeft
        list.performMouseInput {
            moveTo(rest - Offset(24f, 0f))
            moveTo(rest)
        }
        compose.waitForIdle()
        assertEquals("Uzun 03", label(selectedRow()))
        // twenty rows down, the list moving under the pointer
        repeat(20) {
            field.performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        assertEquals("Uzun 23", label(selectedRow()))
        assertWhole(selectedRow())
        // the pointer moves: the row under it now, and the list stays where it is
        val under = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
            .single { (rest + listBounds().topLeft).let { at -> at.y >= it.boundsInRoot.top && at.y < it.boundsInRoot.bottom } }
        assertTrue(label(under), label(under) != "Uzun 23" && label(under) != "Uzun 03")
        list.performMouseInput { moveTo(rest + Offset(8f, 0f)) }
        compose.waitForIdle()
        assertEquals(label(under), label(selectedRow()))
        assertEquals(under.boundsInRoot, selectedRow().boundsInRoot)
    }

    /** 1.0.7: Enter held on the line shows them all once: its repeats run nothing (they ran the first of what the line listed). */
    @Test
    fun enterHeldOnTheLineRunsNothing() {
        palette()
        field.performTextInput("a")
        compose.waitForIdle()
        repeat(6) { field.performKeyInput { pressKey(Key.DirectionDown) } }
        compose.waitForIdle()
        assertTrue("no hit is selected on the line", rows().none { it.second })
        // held for a second: the key repeats after half of it
        field.performKeyInput { pressKey(Key.Enter, 1_000L) }
        compose.waitForIdle()
        assertTrue(ran.toString(), ran.isEmpty())
        assertTrue(open)
        assertTrue(more().isEmpty())
        assertTrue(rows().size > 6)
    }

    /** 1.0.7: while the library is read for the first time its media are not there yet: said, where they would be listed. */
    @Test
    fun whileTheLibraryIsReadItSaysSo() {
        palette(reading = true)
        field.performTextInput("kuzey")
        compose.waitForIdle()
        compose.onNodeWithText("Kütüphane okunuyor…").assertExists()
        compose.onNodeWithText("Eşleşen bir şey yok").assertDoesNotExist()
        // commands asked for: the library is not waited for
        field.performTextInput("qq")
        compose.waitForIdle()
        compose.onNodeWithText("Kütüphane okunuyor…").assertExists()
        field.performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        compose.onNodeWithText("Kütüphane okunuyor…").assertDoesNotExist()
        compose.onNodeWithText("Eşleşen bir şey yok").assertExists()
    }

    @Test
    fun withoutAQueryTheRecentComeFirst() {
        palette(recent = listOf("screen:${Destination.Settings.route}", "media:sabaha", "gone:item"))
        compose.onNodeWithText("SON KULLANILANLAR").assertExists()
        val labels = rows().map { it.first }
        assertEquals(listOf("Ayarlar", "Sabaha Kadar"), labels.take(2))
        // then the commands
        assertTrue(labels.size > 2)
        compose.onNodeWithTag("palette").assertExists()
    }
}
