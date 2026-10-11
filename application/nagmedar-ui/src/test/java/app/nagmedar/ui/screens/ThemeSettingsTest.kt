package app.nagmedar.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.AccentTones
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagColors
import app.nagmedar.ui.theme.NagPalette
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.SystemBarsFollowTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.theme.contrastIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-204: theme and accent choices apply at once, system bar icons included; the wallpaper accent is offered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class ThemeSettingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var colors: NagColors? = null

    private fun render(theme: ThemeMode, accent: Accent): NagmedarPreferences {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("theme-settings-test", Context.MODE_PRIVATE))
        prefs.updateThemeMode(theme)
        prefs.updateAccent(accent)
        compose.setContent {
            NagmedarTheme(themeMode = prefs.themeMode, accent = prefs.accent, inputMode = InputMode.Pointer) {
                colors = NagTheme.colors
                SystemBarsFollowTheme()
                AppearanceSection(prefs)
            }
        }
        compose.waitForIdle()
        return prefs
    }

    /** Dark status bar icons, as on a light background. */
    private fun darkBarIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test
    fun choicesApplyAtOnce() {
        val prefs = render(ThemeMode.Dark, Accent.Blue)
        assertTrue(colors!!.isDark)
        assertFalse("light icons on the dark theme", darkBarIcons())

        click("Açık")
        assertEquals(ThemeMode.Light, prefs.themeMode)
        assertEquals(NagPalette.light(Accent.Blue).background, colors!!.background)
        assertTrue("dark icons on the light theme", darkBarIcons())

        compose.onNodeWithContentDescription("Mercan").performClick()
        compose.waitForIdle()
        assertEquals(NagPalette.light(Accent.Coral).accent, colors!!.accent)

        click("OLED siyah")
        assertEquals(Color.Black, colors!!.background)
        assertEquals(NagPalette.oled(Accent.Coral).selection, colors!!.selection)
        assertFalse(darkBarIcons())
    }

    @Test
    fun theWallpaperColourIsOffered() {
        val prefs = render(ThemeMode.Light, Accent.Blue)
        val seed = Color(compose.activity.getColor(android.R.color.system_accent1_500))
        compose.onNodeWithContentDescription("Duvar kâğıdı rengi").performClick()
        compose.waitForIdle()
        assertEquals(Accent.System, prefs.accent)
        assertEquals(AccentTones.fromSeed(seed).deep, colors!!.accent)
        assertTrue(colors!!.contrastIssues().joinToString(), colors!!.contrastIssues().isEmpty())
        compose.onNodeWithText("Duvar kâğıdınızdan alınır; yazılar okunaklı kalsın diye gerektiğinde açılır ya da koyulaşır.").assertExists()
    }
}
