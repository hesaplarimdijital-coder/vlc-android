package app.nagmedar.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
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
 * no query it shows what ran lately.
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

    private fun palette(recent: List<String> = emptyList(), reading: Boolean = false) {
        // while the library is read its media are not among the items
        val items = items().filter { !reading || it.kind != PaletteKind.Media }
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
