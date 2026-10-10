package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalSplitPane
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuItemRow
import app.nagmedar.ui.components.MenuTitle
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SwatchChoice
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.TableSort
import app.nagmedar.ui.components.rememberSplitState
import app.nagmedar.ui.sampleMovies
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-202: every desktop component has a visible hover, keyboard focus and pressed state.
 *
 * Each component is driven into each state with real input (mouse move, focus request in keyboard
 * mode, finger down) and its area is compared with the resting state: a state that changes
 * nothing on screen fails the test. The crops are stitched into components-<theme>.png
 * (rows: components, columns: normal, hover, focus, pressed) for the screenshot review.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w900dp-h1100dp-mdpi")
class ComponentStatesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModeManager: InputModeManager
    private lateinit var focusManager: FocusManager

    private class Sample(
        val name: String,
        /** what is cut out of the window */
        val area: ComponentStatesTest.() -> SemanticsNodeInteraction,
        /** what receives the input */
        val target: ComponentStatesTest.() -> SemanticsNodeInteraction = area,
        val margin: Dp = 4.dp,
    )

    private val samples = listOf(
        Sample("Simge düğmesi", { compose.onNodeWithTag("icon-button") }),
        Sample("Düğme", { compose.onNodeWithTag("button") }),
        Sample("Birincil düğme", { compose.onNodeWithTag("button-primary") }),
        Sample("Segment seçimi", { compose.onNodeWithTag("segmented") }, { compose.onNodeWithText("Koyu") }),
        Sample("Renk seçimi", { compose.onNodeWithTag("swatches") }, { compose.onNodeWithContentDescription("Kehribar") }),
        Sample("Menü başlığı", { compose.onNodeWithTag("menu-title") }),
        Sample("Menü öğesi", { compose.onNodeWithText("Altyazı dosyası ekle…") }),
        Sample("Tablo başlığı", { compose.onNodeWithText("Süre") }),
        Sample("Tablo satırı", { compose.onNodeWithText("Sessiz Liman") }),
        Sample("Kaydırıcı", { compose.onNodeWithTag("slider") }),
        Sample("Bölme ayırıcısı", { compose.onNodeWithContentDescription("Bölme ayırıcısı") }, margin = 12.dp),
    )

    private val states = listOf("Normal", "Üzerinde", "Odak", "Basılı")

    @Composable
    private fun Gallery() {
        inputModeManager = LocalInputModeManager.current
        focusManager = LocalFocusManager.current
        Column(
            Modifier.fillMaxSize().background(NagTheme.colors.background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NagIconButton(NagIcons.Play, "Oynat", {}, Modifier.testTag("icon-button"))
            NagButton("Klasör seç", {}, Modifier.testTag("button"), icon = NagIcons.Folder)
            NagButton("Dönüştür", {}, Modifier.testTag("button-primary"), primary = true)
            SegmentedChoice(listOf(1 to "Açık", 2 to "Koyu", 3 to "OLED"), selected = 1, onSelect = {}, modifier = Modifier.testTag("segmented"))
            SwatchChoice(
                listOf(Triple(1, Accent.Blue.base, "Mavi"), Triple(2, Accent.Amber.base, "Kehribar"), Triple(3, Accent.Green.base, "Yeşil")),
                selected = 1, onSelect = {}, modifier = Modifier.testTag("swatches"),
            )
            MenuTitle("Görünüm", open = false, onClick = {}, modifier = Modifier.testTag("menu-title"))
            Box(Modifier.width(280.dp)) {
                MenuItemRow(MenuEntry.Item("Altyazı dosyası ekle…", {}, shortcut = "Ctrl+Shift+S", icon = NagIcons.Subtitle)) {}
            }
            Box(Modifier.width(560.dp).height(120.dp)) {
                DenseTable(
                    columns = listOf(
                        TableColumn("Başlık", ColumnWidth.Weight(1f)) { it.title },
                        TableColumn("Biçim", ColumnWidth.Fixed(140.dp), mono = true) { it.resolution },
                        TableColumn("Süre", ColumnWidth.Fixed(100.dp), alignEnd = true, mono = true) { it.duration },
                    ),
                    rows = sampleMovies.take(2),
                    key = { it.title },
                    onRowClick = { _, _ -> },
                    sort = TableSort(0, ascending = true),
                    onSortChange = {},
                )
            }
            NagSlider(value = 0.3f, onValueChange = {}, modifier = Modifier.width(320.dp).testTag("slider"))
            Box(Modifier.size(420.dp, 80.dp)) {
                HorizontalSplitPane(
                    state = rememberSplitState(200.dp),
                    start = { Box(Modifier.fillMaxSize().background(NagTheme.colors.surface)) },
                    end = { Box(Modifier.fillMaxSize().background(NagTheme.colors.surface)) },
                    minStart = 100.dp,
                    minEnd = 100.dp,
                    dividerLabel = "Bölme ayırıcısı",
                )
            }
        }
    }

    private fun window(): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun crop(sample: Sample): Bitmap {
        val full = window()
        val bounds = sample.area(this).fetchSemanticsNode().boundsInWindow
        val margin = with(compose.density) { sample.margin.toPx() }
        val left = (bounds.left - margin).toInt().coerceIn(0, full.width - 1)
        val top = (bounds.top - margin).toInt().coerceIn(0, full.height - 1)
        val right = (bounds.right + margin).toInt().coerceIn(left + 1, full.width)
        val bottom = (bounds.bottom + margin).toInt().coerceIn(top + 1, full.height)
        return Bitmap.createBitmap(full, left, top, right - left, bottom - top)
    }

    /** Share of pixels whose colour moved by more than a few levels in any channel. */
    private fun changed(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        var count = 0
        for (i in pa.indices) {
            val x = pa[i]
            val y = pb[i]
            val d = max(abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF)), max(abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF)), abs((x and 0xFF) - (y and 0xFF))))
            if (d > 3) count++
        }
        return count.toDouble() / pa.size
    }

    private fun parkMouse() {
        compose.onRoot().performMouseInput { moveTo(Offset(2f, 2f)) }
    }

    private fun captureStates(sample: Sample): List<Bitmap> {
        parkMouse()
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        val normal = crop(sample)

        sample.target(this).performMouseInput { moveTo(center) }
        val hover = crop(sample)
        parkMouse()

        // pressed before focused, so no focus ring is left over in the pressed picture
        sample.target(this).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250) // presses inside scrolling lists are reported after the tap timeout
        val pressed = crop(sample)
        sample.target(this).performTouchInput { cancel() }

        compose.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }
        sample.target(this).requestFocus()
        val focus = crop(sample)
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        return listOf(normal, hover, focus, pressed)
    }

    private fun run(theme: ThemeMode, file: String) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Pointer) { Gallery() }
        }
        compose.waitForIdle()
        // a tap on the empty margin first puts the window in touch mode, as any real touch would: in keyboard
        // mode the first press would also focus its button
        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        val rows = samples.map { it to captureStates(it) }
        val colors = if (theme == ThemeMode.Light) NagPalette.light(Accent.Blue) else NagPalette.dark(Accent.Blue)
        ShellScreenshotTest.save(file, sheet(rows, colors.background, colors.text, colors.textTertiary))
        val missing = rows.flatMap { (sample, shots) ->
            (1..3).filter { changed(shots[0], shots[it]) < 0.01 }.map { "${sample.name}: ${states[it]}" }
        }
        assertTrue("no visible state: " + missing.joinToString(), missing.isEmpty())
    }

    /** The crops in a grid with the component names on the left and the states on top. */
    private fun sheet(rows: List<Pair<Sample, List<Bitmap>>>, background: Color, text: Color, caption: Color): Bitmap {
        val gap = 16
        val labelWidth = 150
        val header = 28
        val columnWidth = rows.maxOf { (_, shots) -> shots.maxOf { it.width } } + gap
        val rowHeights = rows.map { (_, shots) -> shots.maxOf { it.height } + gap }
        val sheet = Bitmap.createBitmap(labelWidth + columnWidth * states.size + gap, header + rowHeights.sum() + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(background.toArgb())
        val names = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text.toArgb(); textSize = 13f }
        val heads = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = caption.toArgb(); textSize = 12f }
        states.forEachIndexed { i, state -> canvas.drawText(state, (labelWidth + i * columnWidth).toFloat(), 18f, heads) }
        var y = header
        rows.forEachIndexed { r, (sample, shots) ->
            canvas.drawText(sample.name, gap.toFloat(), y + rowHeights[r] / 2f, names)
            shots.forEachIndexed { i, shot -> canvas.drawBitmap(shot, (labelWidth + i * columnWidth).toFloat(), y.toFloat(), null) }
            y += rowHeights[r]
        }
        return sheet
    }

    @Test
    fun darkStates() = run(ThemeMode.Dark, "components-dark")

    @Test
    fun lightStates() = run(ThemeMode.Light, "components-light")
}
