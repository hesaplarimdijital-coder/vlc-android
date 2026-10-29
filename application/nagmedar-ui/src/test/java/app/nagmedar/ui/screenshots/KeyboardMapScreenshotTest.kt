package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.input.KeyLayout
import app.nagmedar.ui.input.PhysicalKeys
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.TestLayouts
import app.nagmedar.ui.screens.ShortcutEditor
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-410: the keyboard map in the shortcut editor: in DeX with a Türkçe Q keyboard, Ctrl+Ö pressed
 * on it; on a tablet with a US keyboard and the Ctrl layer chosen; on a phone without a keyboard.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardMapScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun reset() {
        PhysicalKeys.reset()
        ShortcutRegistry.resume()
    }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, keyboard: KeyLayout?, then: () -> Unit = {}) {
        // keys the test presses come from the virtual keyboard's id
        if (keyboard != null) TestLayouts.use(-1 to keyboard) else TestLayouts.use(keyboard = null)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ShortcutEditor(emptyMap(), {}) }
                }
            }
        }
        compose.waitForIdle()
        // the button by its name in the test's language
        compose.onNode(hasText(compose.activity.getString(R.string.nag_kb_show)) and hasClickAction()).performClick()
        compose.waitForIdle()
        then()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun dex() = capture("keymap-dex-dark", ThemeMode.Dark, InputMode.Pointer, TestLayouts.turkishQ) {
        compose.onNodeWithContentDescription("Ğ · Yavaşlat").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("keyboard-map").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Equals) } }
    }

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("keymap-tablet-light", ThemeMode.Light, InputMode.Touch, TestLayouts.us) {
        // by touch the chips choose the layer
        compose.onNode(hasText("Ctrl") and hasClickAction()).performClick()
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("keymap-phone-dark", ThemeMode.Dark, InputMode.Touch, keyboard = null)

    /** S-1501: in English. */
    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun phoneEnglish() = capture("keymap-phone-dark-en", ThemeMode.Dark, InputMode.Touch, keyboard = null)
}
