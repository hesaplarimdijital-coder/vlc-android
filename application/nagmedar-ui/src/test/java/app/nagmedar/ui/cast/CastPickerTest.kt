package app.nagmedar.ui.cast

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.input.InputMode as ComposeInputMode

/**
 * S-1302: the device picker: the devices found and this device, chosen by mouse, finger or
 * keyboard; the one cast to marked; what to check when nothing answers; what is not cast as it is.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class CastPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val salon = CastDevice.of("Salon TV", "chromecast")
    private val kitchen = CastDevice.of("Mutfak", "stream_out_dlna")
    private val chosen = mutableListOf<CastDevice>()
    private var here = 0
    private var searchedAgain = 0
    private lateinit var inputModes: InputModeManager

    private fun show(view: CastPickerView, active: CastDevice? = null, notices: List<CastNotice> = emptyList()) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                inputModes = LocalInputModeManager.current
                CastPickerSurface(
                    view = view,
                    active = active,
                    notices = notices,
                    onChoose = { chosen += it },
                    onThisDevice = { here++ },
                    onClose = {},
                    onSearchAgain = { searchedAgain++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun row(name: String) = compose.onNode(hasText(name) and hasClickAction())

    @Test
    fun aDeviceIsChosenAndThisDeviceTakesItBack() {
        show(CastPickerView.Devices(listOf(salon, kitchen), searching = true))
        compose.onNodeWithText("Chromecast").assertExists()
        compose.onNodeWithText("DLNA cihazı").assertExists()
        compose.onNodeWithText("Cihazlar yanıt verdikçe görünür").assertExists()
        row("Bu cihaz").assertIsSelected()
        row("Mutfak").performClick()
        assertEquals(listOf(kitchen), chosen)
        row("Bu cihaz").performClick()
        assertEquals(1, here)
    }

    @Test
    fun theDeviceCastToIsMarked() {
        show(CastPickerView.Devices(listOf(salon, kitchen), searching = false), active = salon)
        row("Salon TV").assertIsSelected()
        row("Mutfak").assertIsNotSelected()
        row("Bu cihaz").assertIsNotSelected()
        compose.onNodeWithText("Yayınlanıyor · Chromecast").assertExists()
        compose.onNodeWithText("Yayın durur; burada sürer").assertExists()
    }

    @Test
    fun theKeyboardChoosesWithEnter() {
        show(CastPickerView.Devices(listOf(salon, kitchen), searching = false))
        // a hardware key takes the window out of touch mode; injected keys do not
        compose.runOnIdle { inputModes.requestInputMode(ComposeInputMode.Keyboard) }
        row("Salon TV").requestFocus()
        row("Salon TV").assertIsFocused()
        row("Salon TV").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf(salon), chosen)
    }

    @Test
    fun searching() {
        show(CastPickerView.Searching)
        compose.onNodeWithText("Bu ağdaki cihazlar aranıyor…").assertExists()
        row("Bu cihaz").assertIsSelected()
    }

    @Test
    fun nothingFoundSaysWhatToCheck() {
        show(CastPickerView.Empty)
        compose.onNodeWithText("Cihaz bulunamadı").assertExists()
        compose.onNodeWithText("Bu cihaz ve TV aynı Wi-Fi'de, misafir ağında değil.").assertExists()
        compose.onNodeWithText("Yeniden ara").performClick()
        assertEquals(1, searchedAgain)
    }

    @Test
    fun whatCannotBeCastIsSaid() {
        show(CastPickerView.Devices(listOf(salon), searching = false), notices = listOf(CastNotice.TrackerHere))
        compose.onNodeWithTag("cast-notice").assertExists()
        compose.onNodeWithText("Bu parça bir tracker modülü: yalnızca bu cihazda çalar. Ondan sonraki parçalar yayınlanır.").assertExists()
    }
}
