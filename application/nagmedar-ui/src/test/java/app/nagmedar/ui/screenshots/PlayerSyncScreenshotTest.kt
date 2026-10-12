package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.player.refresh.RefreshMatch
import app.nagmedar.ui.player.refresh.RefreshSection
import app.nagmedar.ui.player.refresh.RefreshStatus
import app.nagmedar.ui.player.refresh.ScreenKind
import app.nagmedar.ui.player.refresh.samplePhone
import app.nagmedar.ui.player.refresh.sampleTablet
import app.nagmedar.ui.player.refresh.sampleTv
import app.nagmedar.ui.player.sync.SubtitleRate
import app.nagmedar.ui.player.sync.SyncPanel
import app.nagmedar.ui.player.sync.SyncValues
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-604: the player with its sync tab, after the mockups' "Panel · Sync": a film whose sound
 * comes 120 ms late and whose PAL subtitles are slowed to the film, remembered; in English; on a
 * phone, for a finger; and with this build's player, whose subtitle speed is still to come.
 * S-612: its refresh rate matching: the film on a tablet's 120 Hz panel, "Always"; on a TV over
 * HDMI at the film's rate; on a phone's 60 Hz; a PAL video with matching off.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSyncScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val film = SyncValues(audioDelayMs = 120, subtitleDelayMs = -350, subtitleRate = SubtitleRate.PalToFilm)

    private fun capture(
        name: String,
        values: SyncValues,
        supportsRate: Boolean,
        remembered: Boolean = true,
        input: InputMode = InputMode.Pointer,
        controls: Boolean = true,
        screen: RefreshStatus = sampleTablet,
        match: RefreshMatch = RefreshMatch.Always,
    ) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(PanelTab.Sync, controls) { columns ->
                    SyncPanel(values, remembered, supportsRate, {}, {}, {}, {}, {}, columns = columns, refresh = { RefreshSection(screen, match) {} })
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
    fun dex() = capture("player-sync-dex", film, supportsRate = true)

    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun dexEnglish() = capture("player-sync-dex-en", film, supportsRate = true)

    @Test @Config(qualifiers = "tr-w728dp-h880dp-mdpi")
    fun halfWindow() = capture("player-sync-half", film, supportsRate = true, screen = sampleTv)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-sync-phone", film, supportsRate = true, input = InputMode.Touch, controls = false, screen = samplePhone, match = RefreshMatch.Seamless)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun thisBuildsPlayer() = capture(
        "player-sync-pending",
        SyncValues(audioDelayMs = -40),
        supportsRate = false,
        remembered = false,
        screen = RefreshStatus(fps = 25.0, hz = 60.0, screen = ScreenKind.Tablet, rates = listOf(60.0, 120.0)),
        match = RefreshMatch.Off,
    )
}
