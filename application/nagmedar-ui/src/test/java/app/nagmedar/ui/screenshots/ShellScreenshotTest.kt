package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.R
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.screens.MoreAction
import app.nagmedar.ui.screens.MoreActionGroup
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * S-1402: the shell in the five width classes, light and dark, in Turkish. The PNGs land in
 * build/outputs/screenshots; CI compares them with src/test/screenshots and reports the
 * differences (buildsystem/ci/screenshot_report.py).
 *
 * The window is drawn into a bitmap directly: captureToImage() waits for a frame callback that
 * Robolectric's paused main looper never delivers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(
        name: String,
        widthClass: WidthClass,
        theme: ThemeMode,
        input: InputMode,
        start: Destination = Destination.Music,
        /** 1.0.1: what is tapped after the start, in order (the phone's "⋯" tab, then a row of its page). */
        taps: List<Int> = emptyList(),
    ) {
        // the test activity's theme has an action bar; the app's theme has none
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                    TestShell(widthClass, startDestination = start, moreActions = playbackActions())
                }
            }
        }
        compose.waitForIdle()
        taps.forEach { label ->
            compose.onNodeWithText(compose.activity.getString(label)).performClick()
            compose.waitForIdle()
        }
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactDark() = capture("compact-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactLight() = capture("compact-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun mediumDark() = capture("medium-dark", WidthClass.Medium, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w700dp-h1000dp-mdpi")
    fun mediumLight() = capture("medium-light", WidthClass.Medium, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun expandedDark() = capture("expanded-dark", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun expandedLight() = capture("expanded-light", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun largeDark() = capture("large-dark", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun largeLight() = capture("large-light", WidthClass.Large, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi-keysexposed-qwerty")
    fun extraLargeDark() = capture("xlarge-dark", WidthClass.ExtraLarge, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi-keysexposed-qwerty")
    fun extraLargeLight() = capture("xlarge-light", WidthClass.ExtraLarge, ThemeMode.Light, InputMode.Pointer)

    @Test @Config(qualifiers = "tr-w1920dp-h1080dp-mdpi-keysexposed-qwerty")
    fun extraLargeOled() = capture("xlarge-oled-podcasts", WidthClass.ExtraLarge, ThemeMode.Oled, InputMode.Pointer, Destination.Podcasts)

    /** S-1501: the shell in English, in a DeX window and on a phone. */
    @Test @Config(qualifiers = "en-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun largeDarkEnglish() = capture("large-dark-en", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer)

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun compactLightEnglish() = capture("compact-light-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch)

    /** 1.0.1: the phone's "⋯" tab is the "Daha fazla" page: the other pages by section, then "Oynatma". */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactMorePageDark() = capture("compact-more-page-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch, taps = listOf(R.string.nag_nav_more))

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactMorePageLight() = capture("compact-more-page-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, taps = listOf(R.string.nag_nav_more))

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun compactMorePageLightEnglish() = capture("compact-more-page-light-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, taps = listOf(R.string.nag_nav_more))

    /** 1.0.1: Settings from "Daha fazla", a page of its own: the back arrow, no bottom bar, no mini player. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactSettingsPageDark() = capture(
        "compact-settings-page-dark", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch, taps = listOf(R.string.nag_nav_more, R.string.nag_nav_settings),
    )

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun compactSettingsPageLightEnglish() = capture(
        "compact-settings-page-light-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, taps = listOf(R.string.nag_nav_more, R.string.nag_nav_settings),
    )

    /** 1.0.1: a content page from "⋯" (Network): the back arrow, the bars, "⋯" lit. */
    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun compactNetworkPageLight() = capture(
        "compact-network-page-light", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, taps = listOf(R.string.nag_nav_more, R.string.nag_nav_network),
    )

    @Test @Config(qualifiers = "en-w411dp-h891dp-hdpi")
    fun compactNetworkPageDarkEnglish() = capture(
        "compact-network-page-dark-en", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch, taps = listOf(R.string.nag_nav_more, R.string.nag_nav_network),
    )

    /** The app's "Oynatma" group of the page (NagmedarApp's). */
    @Composable
    private fun playbackActions() = listOf(
        MoreActionGroup(
            stringResource(R.string.nag_menu_playback),
            listOf(
                MoreAction(stringResource(R.string.nag_eq_title), NagIcons.Equalizer, {}, opensPage = true),
                MoreAction(stringResource(R.string.nag_sc_sleep_timer), NagIcons.Sleep, {}),
                MoreAction(stringResource(R.string.nag_queue_title), NagIcons.Queue, {}),
            ),
        ),
    )

    companion object {
        private val dir = File(System.getProperty("nagmedar.screenshotDir") ?: "build/outputs/screenshots")

        fun save(name: String, bitmap: Bitmap) {
            dir.mkdirs()
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
