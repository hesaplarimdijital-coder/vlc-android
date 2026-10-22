package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.tvlab.BuiltInProfiles
import app.nagmedar.ui.tvlab.NoTvLabActions
import app.nagmedar.ui.tvlab.ProfileDraft
import app.nagmedar.ui.tvlab.ProfileEditorSurface
import app.nagmedar.ui.tvlab.TvLabContent
import app.nagmedar.ui.tvlab.TvLabView
import app.nagmedar.ui.tvlab.sampleRunningView
import app.nagmedar.ui.tvlab.sampleTvLabView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1201 … S-1204, for approval: the TV Lab with the sample film (a UHD remux with Dolby Vision
 * profile 7, DTS-HD and PGS) against the LG OLED B4, in a DeX window (as the mockup, and the
 * whole page), on a tablet and on a phone (the whole page); while its packets are read; before a
 * file is chosen; and the profile editor.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvLabScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, widthClass: WidthClass, theme: ThemeMode, input: InputMode, view: TvLabView = sampleTvLabView(), over: @Composable () -> Unit = {}) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = Destination.TvLab) { _, _ -> TvLabContent(view, NoTvLabActions) }
                    over()
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun dex() = capture("tvlab-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1280dp-h1400dp-mdpi")
    fun dexWholePage() = capture("tvlab-dex-dark-tall", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("tvlab-dex-dark-en", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun tablet() = capture("tvlab-tablet-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h2600dp-hdpi")
    fun phoneWholePage() = capture("tvlab-phone-dark-tall", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun running() = capture("tvlab-running-tablet-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, sampleRunningView())

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun empty() = capture(
        "tvlab-empty-dex-light",
        WidthClass.Large,
        ThemeMode.Light,
        InputMode.Pointer,
        TvLabView.empty(BuiltInProfiles.lgB4Usb, BuiltInProfiles.all),
    )

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun editor() = capture("tvlab-editor-dex-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer) {
        // the dialog's dim, drawn here: the window holds only the activity
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            ProfileEditorSurface(ProfileDraft(BuiltInProfiles.lgB4Usb, isNew = false), saved = true, onSave = {}, onCancel = {}, onShare = {}, onExport = {}, onRemove = {})
        }
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun editorPhone() = capture("tvlab-editor-phone-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            ProfileEditorSurface(ProfileDraft(BuiltInProfiles.lgB4Usb.copy(builtIn = false), isNew = true), saved = false, onSave = {}, onCancel = {}, onShare = {}, onExport = {}, onRemove = {})
        }
    }
}
