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
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.player.record.RecordingSection
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
 * S-607: Settings › Recording: what Shift+R records and the folder; a folder on a drive chosen in
 * DeX, the default ones on a tablet and a phone, and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingSettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, chosen: Boolean) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("recording-shot-$name", Context.MODE_PRIVATE))
        if (chosen) prefs.updateRecordingFolder("content://com.android.externalstorage.documents/tree/1234-ABCD%3AKay%C4%B1tlar")
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { RecordingSection(prefs) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h360dp-mdpi")
    fun dex() = capture("settings-recording-dex-dark", ThemeMode.Dark, InputMode.Pointer, chosen = true)

    @Test @Config(qualifiers = "tr-w800dp-h400dp-mdpi")
    fun tablet() = capture("settings-recording-tablet-light", ThemeMode.Light, InputMode.Touch, chosen = false)

    @Test @Config(qualifiers = "tr-w390dp-h860dp-hdpi")
    fun phone() = capture("settings-recording-phone-dark", ThemeMode.Dark, InputMode.Touch, chosen = false)

    @Test @Config(qualifiers = "en-w800dp-h400dp-mdpi")
    fun tabletEnglish() = capture("settings-recording-tablet-light-en", ThemeMode.Light, InputMode.Touch, chosen = true)
}
