package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.music.EbruBand
import app.nagmedar.ui.music.EbruInks
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
 * 1.1.0 (Ebru), for approval: the marbled band the Music page opens with, made on the device: the bozkır's inks while
 * nothing plays, and a cover's (a night sky's blues and a sun's amber) as Ebru.inksOf would find them; its torn edge
 * over the page, the masthead under it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EbruScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val night = EbruInks(
        listOf(0xFF1F2A5A.toInt(), 0xFFE7A33E.toInt(), 0xFF2E9C9A.toInt(), 0xFFB4412F.toInt(), 0xFF121833.toInt()),
        0xFFEFE3CC.toInt(),
        101L,
    )

    private fun capture(name: String, theme: ThemeMode, input: InputMode, inks: EbruInks, height: Int) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Column(Modifier.fillMaxSize().background(NagTheme.colors.background), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EbruBand(inks, height.dp)
                    Text("Müzik", style = NagTheme.type.masthead, color = NagTheme.colors.text, modifier = Modifier.padding(horizontal = 16.dp))
                    Text("974 parça · 88 albüm", style = NagTheme.type.body, color = NagTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w411dp-h420dp-hdpi")
    fun phoneDark() = capture("ebru-band-phone-dark", ThemeMode.Dark, InputMode.Touch, EbruInks.Default, 84)

    @Test @Config(qualifiers = "tr-w1280dp-h400dp-mdpi")
    fun dexLight() = capture("ebru-band-dex-light", ThemeMode.Light, InputMode.Pointer, night, 104)
}
