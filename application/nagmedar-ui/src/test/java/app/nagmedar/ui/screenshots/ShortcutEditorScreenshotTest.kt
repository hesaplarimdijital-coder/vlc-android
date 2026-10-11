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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutPreset
import app.nagmedar.ui.input.ShortcutRegistry
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
 * S-402: the shortcut editor in a DeX window, one shortcut changed and another recording keys;
 * on a tablet by touch, one shortcut changed and one without keys; on a phone, the mpv preset with
 * a conflict made by hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutEditorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun reset() {
        ShortcutRegistry.recording = false
    }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, overrides: Map<ShortcutId, KeyChord?>, recording: ShortcutId? = null) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ShortcutEditor(overrides, {}) }
                }
            }
        }
        compose.waitForIdle()
        if (recording != null) {
            compose.onNodeWithTag("keys-${recording.name}").performClick()
            compose.waitForIdle()
        }
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture(
        "shortcuts-dex-dark", ThemeMode.Dark, InputMode.Pointer,
        mapOf(ShortcutId.Stop to KeyChord(Key.Q, ctrl = true)),
        recording = ShortcutId.Next,
    )

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture(
        "shortcuts-tablet-dark", ThemeMode.Dark, InputMode.Touch,
        mapOf(ShortcutId.Mute to null, ShortcutId.Stop to KeyChord(Key.Q, ctrl = true)),
    )

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture(
        "shortcuts-phone-light", ThemeMode.Light, InputMode.Touch,
        ShortcutPreset.Mpv.overrides + (ShortcutId.Repeat to KeyChord(Key.M)),
    )
}
