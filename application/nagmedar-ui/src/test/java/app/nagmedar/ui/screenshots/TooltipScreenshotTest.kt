package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.TooltipBubble
import app.nagmedar.ui.components.TooltipDelay
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
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
 * S-404: icon buttons with the tooltips a resting mouse brings, names and keys from the shortcut
 * table (drawn in place: a tooltip is a window of its own), and the delay setting.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TooltipScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Tipped(icon: ImageVector, name: String, shortcut: ShortcutId) {
        val keys = ShortcutRegistry.chordLabel(shortcut, LocalContext.current.resources)
        // as wide as the longest name with its keys in a DeX window's text (one sp larger since Desktop Kit 0.2.0)
        Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NagIconButton(icon, name, {}, shortcut = shortcut)
            TooltipBubble(name, keys, Modifier.padding(top = 6.dp))
        }
    }

    private fun capture(name: String, theme: ThemeMode) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        Row {
                            Tipped(NagIcons.Pause, "Duraklat", ShortcutId.PlayPause)
                            Tipped(NagIcons.Next, "Sonraki parça", ShortcutId.Next)
                            Tipped(NagIcons.Shuffle, "Karıştır", ShortcutId.Shuffle)
                            Tipped(NagIcons.ChevronLeft, "Kenar çubuğunu daralt", ShortcutId.ToggleSidebar)
                            Tipped(NagIcons.Search, "Ara", ShortcutId.CommandPalette)
                        }
                        Box(Modifier.width(720.dp)) {
                            SettingRow("İpucu gecikmesi", subtitle = "Fare bir düğmenin üzerinde durunca adı ve kısayolu görünür") {
                                SegmentedChoice(
                                    options = listOf(
                                        TooltipDelay.Off to "Kapalı",
                                        TooltipDelay.Short to "Kısa",
                                        TooltipDelay.Normal to "Normal",
                                        TooltipDelay.Long to "Uzun",
                                    ),
                                    selected = TooltipDelay.Normal,
                                    onSelect = {},
                                )
                            }
                        }
                    }
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
    fun dark() = capture("tooltips-dex-dark", ThemeMode.Dark)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
    fun light() = capture("tooltips-dex-light", ThemeMode.Light)
}
