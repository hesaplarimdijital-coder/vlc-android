package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.remoteaccess.LibraryPart
import app.nagmedar.ui.remoteaccess.RemoteAccessControl
import app.nagmedar.ui.remoteaccess.RemoteAccessSection
import app.nagmedar.ui.remoteaccess.RemoteAccessSettings
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1304: Settings › Remote access: on, with the file and network browsers and the history
 * switched on in DeX; the defaults (off) on a tablet and a phone; and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteAccessScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private object NoControl : RemoteAccessControl {
        override fun start() = Unit

        override fun stop() = Unit

        override fun restart() = Unit

        override fun showAddresses() = Unit

        override fun showHowItWorks() = Unit
    }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, on: Boolean) {
        val prefs = compose.activity.getSharedPreferences("remote-access-shot-$name", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = RemoteAccessSettings(prefs, NoControl)
        if (on) {
            settings.enable(true)
            settings.showLibraryPart(LibraryPart.Search, false)
            settings.showFiles(true)
            settings.showNetwork(true)
            settings.showHistory(true)
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { RemoteAccessSection(settings) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h720dp-mdpi")
    fun dex() = capture("settings-remote-access-dex-dark", ThemeMode.Dark, InputMode.Pointer, on = true)

    @Test @Config(qualifiers = "tr-w800dp-h720dp-mdpi")
    fun tablet() = capture("settings-remote-access-tablet-light", ThemeMode.Light, InputMode.Touch, on = false)

    @Test @Config(qualifiers = "tr-w390dp-h1000dp-hdpi")
    fun phone() = capture("settings-remote-access-phone-dark", ThemeMode.Dark, InputMode.Touch, on = true)

    @Test @Config(qualifiers = "en-w800dp-h720dp-mdpi")
    fun tabletEnglish() = capture("settings-remote-access-tablet-light-en", ThemeMode.Light, InputMode.Touch, on = true)
}
