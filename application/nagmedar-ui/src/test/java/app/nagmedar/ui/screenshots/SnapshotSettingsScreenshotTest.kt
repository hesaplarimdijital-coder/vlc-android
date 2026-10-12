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
import app.nagmedar.ui.player.snapshot.HdrSnapshot
import app.nagmedar.ui.player.snapshot.SnapshotFormat
import app.nagmedar.ui.player.snapshot.SnapshotSettings
import app.nagmedar.ui.screens.SnapshotSection
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
 * S-606: Settings > Screenshots: the format, the subtitles, HDR films and the folder; changed in
 * DeX (WebP, HDR kept, a folder on a drive), as shipped on a tablet and a phone, and in English.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotSettingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String, theme: ThemeMode, input: InputMode, changed: Boolean) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("snapshot-shot-$name", Context.MODE_PRIVATE))
        if (changed) {
            prefs.updateSnapshot(
                SnapshotSettings(
                    format = SnapshotFormat.WebP,
                    subtitles = false,
                    hdr = HdrSnapshot.Keep,
                    folder = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AEkran%20g%C3%B6r%C3%BCnt%C3%BCleri",
                ),
            )
        }
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(top = 16.dp)) { SnapshotSection(prefs) }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h520dp-mdpi")
    fun dex() = capture("settings-snapshot-dex-dark", ThemeMode.Dark, InputMode.Pointer, changed = true)

    @Test @Config(qualifiers = "tr-w800dp-h560dp-mdpi")
    fun tablet() = capture("settings-snapshot-tablet-light", ThemeMode.Light, InputMode.Touch, changed = false)

    @Test @Config(qualifiers = "tr-w390dp-h860dp-hdpi")
    fun phone() = capture("settings-snapshot-phone-dark", ThemeMode.Dark, InputMode.Touch, changed = false)

    @Test @Config(qualifiers = "en-w800dp-h560dp-mdpi")
    fun tabletEnglish() = capture("settings-snapshot-tablet-light-en", ThemeMode.Light, InputMode.Touch, changed = false)
}
