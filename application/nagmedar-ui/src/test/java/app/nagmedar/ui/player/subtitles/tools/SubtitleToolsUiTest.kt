package app.nagmedar.ui.player.subtitles.tools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-708: the tools' window as a person uses it. The line timing editor marks the lines with a
 * wrong time (an error, which blocks Save) and the overlapping ones (a warning, which does not),
 * says so in words for a screen reader, and lets Save through once the times are mended; the keys
 * walk the lines; the cleaning shows its changes and the new file's name is the one to edit.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SubtitleToolsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var io: FakeToolsIo
    private lateinit var controller: SubtitleToolsController
    private var closed = 0
    private lateinit var inputModes: InputModeManager

    private fun window(text: String, tab: ToolsTab, playback: ToolsPlayback? = null) {
        val file = SAMPLE_FILE
        io = FakeToolsIo(mutableMapOf(file.uri to text.toByteArray(Charsets.UTF_8)))
        controller = toolsController(text = text, file = file, io = io, playback = playback)
        controller.setTab(tab)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModes = LocalInputModeManager.current
                SubtitleToolsSurface(controller, onOpenFile = {}, onDismiss = { closed++ })
            }
        }
        compose.waitForIdle()
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
    }

    private fun description(id: Long): String? =
        compose.onNodeWithTag("cue-row-$id").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun theLinesWithAWrongTimeAreSaidSoAndSaveWaits() {
        window(FAULTY_SRT, ToolsTab.Edit)
        compose.onNodeWithTag("cue-summary").assertTextContains("1 satırın zamanı hatalı · 2 çakışma")
        // by words, not only by colour: the row says what is wrong with it
        assertEquals("Sonraki satırla çakışıyor", description(2))
        assertEquals("Sonraki satırla çakışıyor", description(3))
        assertEquals("Başlamadan bitiyor", description(4))
        assertEquals(null, description(1))
        compose.onNodeWithTag("save-button").assertIsNotEnabled()
        compose.onNodeWithTag("save-status").assertTextContains("Kaydetmeden önce zamanı hatalı satırları düzeltin")
        compose.onNodeWithTag("cue-next-issue").assertIsEnabled()
    }

    @Test
    fun mendingTheTimeLetsSaveThrough() {
        window(FAULTY_SRT, ToolsTab.Edit)
        compose.onNodeWithTag("cue-row-4").performClick()
        compose.onNodeWithTag("cue-end").performTextReplacement("0:00:10,900")
        compose.waitForIdle()
        assertEquals(10_900L, controller.state?.document?.cues?.first { it.id == 4L }?.endMs)
        // the overlap stays, as a warning
        compose.onNodeWithTag("cue-summary").assertTextContains("2 çakışma")
        assertEquals(null, description(4))
        compose.onNodeWithTag("save-button").assertIsEnabled()
        compose.onNodeWithTag("save-button").performClick()
        compose.waitForIdle()
        assertEquals("Kuzey Işığı.tr.duzenlendi.srt", io.saved.single().name)
        compose.onNodeWithTag("save-status").assertTextContains("Kuzey Işığı.tr.duzenlendi.srt olarak özgün dosyanın yanına kaydedildi")
    }

    @Test
    fun aTimeThatIsNoTimeChangesNothing() {
        window(FAULTY_SRT, ToolsTab.Edit)
        compose.onNodeWithTag("cue-row-1").performClick()
        compose.onNodeWithTag("cue-start").performTextReplacement("hemen")
        compose.waitForIdle()
        assertEquals(2_400L, controller.state?.document?.cues?.first()?.startMs)
        assertFalse(controller.state?.changed == true)
    }

    @Test
    fun theKeysWalkTheLines() {
        window(SDH_SRT, ToolsTab.Edit)
        compose.onNodeWithTag("cue-row-1").requestFocus()
        compose.onNodeWithTag("cue-row-1").assertIsFocused()
        compose.onNodeWithTag("cue-row-1").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(2L, controller.state?.focus)
        compose.onNodeWithTag("cue-row-2").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(3L, controller.state?.focus)
        compose.onNodeWithTag("cue-row-3").performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals(2L, controller.state?.focus)
    }

    @Test
    fun escapeClosesTheWindow() {
        window(SDH_SRT, ToolsTab.Clean)
        compose.onNodeWithTag("subtools").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun escapeWithUnsavedEditsAsksFirstAndKeepsWorkingOnEscapeAgain() {
        window(SDH_SRT, ToolsTab.Edit)
        compose.runOnIdle { controller.setText(1, "değişti") }
        compose.onNodeWithTag("subtools").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(0, closed)
        compose.onNodeWithTag("subtools-ask").assertExists()
        // the question's three answers, in both languages' words; "keep working" has the focus
        compose.onNodeWithText("Kaydetmeden kapatılsın mı?").assertExists()
        compose.onNodeWithTag("ask-save").assertTextContains("Kaydet")
        compose.onNodeWithTag("ask-discard").assertTextContains("Kaydetmeden kapat")
        compose.onNodeWithTag("ask-keep").assertTextContains("Vazgeç")
        compose.onNodeWithTag("ask-keep").assertIsFocused()
        compose.onNodeWithTag("ask-keep").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        compose.onNodeWithTag("subtools-ask").assertDoesNotExist()
        assertEquals(0, closed)
        assertEquals("değişti", controller.state?.document?.cues?.first()?.text)
    }

    @Test
    fun closingWithoutSavingClosesAndSavingSavesFirst() {
        window(SDH_SRT, ToolsTab.Edit)
        compose.runOnIdle { controller.setText(1, "değişti") }
        compose.onNodeWithTag("subtools").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        compose.onNodeWithTag("ask-save").performClick()
        compose.waitForIdle()
        assertEquals(1, io.saved.size)
        compose.onNodeWithTag("subtools-ask").assertDoesNotExist()
        assertEquals(0, closed)
        // saved: the window now closes without asking
        compose.onNodeWithTag("subtools").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun discardingFromTheQuestionClosesTheWindow() {
        window(SDH_SRT, ToolsTab.Edit)
        compose.runOnIdle { controller.setText(1, "değişti") }
        compose.onNodeWithTag("subtools").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        compose.onNodeWithTag("ask-discard").performClick()
        compose.waitForIdle()
        assertEquals(1, closed)
        assertEquals(0, io.saved.size)
    }

    @Test
    fun withoutAFilmThereAreNoButtonsForItsPosition() {
        window(SDH_SRT, ToolsTab.Edit)
        compose.onNodeWithTag("cue-set-start").assertDoesNotExist()
        compose.onNodeWithTag("cue-set-end").assertDoesNotExist()
        compose.onNodeWithTag("save-load").assertDoesNotExist()
    }

    @Test
    fun withAFilmTheButtonsTakeItsPositionAndEnterSeeksToTheLine() {
        val playback = FakePlayback(position = 9_000)
        window(SDH_SRT, ToolsTab.Edit, playback)
        compose.onNodeWithTag("cue-row-3").performClick()
        compose.onNodeWithTag("cue-set-start").performClick()
        compose.waitForIdle()
        assertEquals(9_000L, controller.state?.document?.cues?.first { it.id == 3L }?.startMs)
        compose.onNodeWithTag("save-load").assertExists()
        compose.onNodeWithTag("cue-row-3").requestFocus()
        compose.onNodeWithTag("cue-row-3").assertIsFocused()
        compose.onNodeWithTag("cue-row-3").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf(9_000L), playback.seeks)
    }

    @Test
    fun cleaningListsTheChangedLinesAndAppliesThem() {
        window(SDH_SRT, ToolsTab.Clean)
        compose.onNodeWithTag("sdh-summary").assertTextContains("9 satır → 7 satır · 2 kaldırıldı, 3 değişti")
        compose.onNodeWithTag("sdh-change-1").assertExists()
        compose.onAllNodesWithText("(satır kaldırıldı)").assertCountEquals(2)
        // the original is not touched yet
        assertEquals(9, controller.state?.document?.cues?.size)
        compose.onNodeWithTag("sdh-apply").performClick()
        compose.waitForIdle()
        assertEquals(7, controller.state?.document?.cues?.size)
        compose.onNodeWithTag("sdh-apply").assertIsNotEnabled()
        compose.onNodeWithTag("sdh-summary").assertTextContains("Bu kurallarla temizlenecek bir şey yok")
    }

    @Test
    fun theFrameRateShowsTheTimesBeforeAndAfter() {
        window(SDH_SRT, ToolsTab.FrameRate)
        // 23.976 to 25 is offered at first; the same rate on both sides and no shift is nothing to apply
        compose.runOnIdle { controller.setRates(FrameRate.Fps25, FrameRate.Fps25) }
        compose.waitForIdle()
        compose.onNodeWithTag("rate-apply").assertIsNotEnabled()
        compose.onNodeWithTag("shift-plus-1000").performClick()
        compose.waitForIdle()
        assertEquals(1_000L, controller.state?.shiftMs)
        compose.onNodeWithTag("rate-apply").assertIsEnabled()
        compose.onNodeWithText("0:00:02,400").assertExists()
        compose.onNodeWithText("0:00:03,400").assertExists()
        compose.onNodeWithText("0:00:31,100").assertExists()
        compose.onNodeWithTag("rate-apply").performClick()
        compose.waitForIdle()
        assertEquals(3_400L, controller.state?.document?.cues?.first()?.startMs)
        assertEquals(0L, controller.state?.shiftMs)
    }

    @Test
    fun theNewNamesBox() {
        window(SDH_SRT, ToolsTab.Clean)
        compose.onNodeWithTag("save-name").performTextReplacement("yeni ad")
        compose.waitForIdle()
        compose.onNodeWithTag("save-button").performClick()
        compose.waitForIdle()
        assertEquals("yeni ad.srt", io.saved.single().name)
    }
}
