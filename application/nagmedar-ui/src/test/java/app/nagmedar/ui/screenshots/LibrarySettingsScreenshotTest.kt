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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.library.LibrarySection
import app.nagmedar.ui.library.LibrarySources
import app.nagmedar.ui.library.LibrarySourcesSection
import app.nagmedar.ui.library.LibraryScopes
import app.nagmedar.ui.library.SourceFolder
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
 * 1.0.3: Settings › Kütüphane with "Kütüphaneyi yeniden tara" under the music's folders: on a
 * phone, on a tablet in English and in DeX, a folder chosen on each.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibrarySettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode) {
        // the music shows one folder of the device's storage, as a user left it
        val stored = compose.activity.getSharedPreferences("library-shot-$name", Context.MODE_PRIVATE)
        val folders = LibraryScopes.Empty.plus(LibrarySection.Music, SourceFolder("/storage/emulated/0/Music"))
        LibrarySources.write(stored.edit().clear(), folders).commit()
        val prefs = NagmedarPreferences(stored)
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) {
                    LibrarySourcesSection(prefs)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("library-rescan").assertExists()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun phone() = capture("settings-library-phone-dark", ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "en-w900dp-h1200dp-mdpi")
    fun tabletEnglish() = capture("settings-library-tablet-light-en", ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h1100dp-mdpi-keysexposed-qwerty")
    fun dex() = capture("settings-library-dex-dark", ThemeMode.Dark, InputMode.Pointer)
}
