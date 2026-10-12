package app.nagmedar.ui.covers

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S-1006: Settings › Covers: Cover Art Archive turned off, the covers kept counted and cleared. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class CoversSectionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun turnedOffAndCleared() {
        val turned = mutableListOf<Boolean>()
        var cleared = 0
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                CoversSection(online = true, kept = 58, onOnlineChange = { turned += it }, onClear = { cleared++ })
            }
        }
        compose.onNodeWithText("Çevrimiçi kapaklar (Cover Art Archive)").assertExists()
        compose.onNodeWithTag("covers-online").assertIsOn().performClick().assertIsOff()
        assertEquals(listOf(false), turned)
        compose.onNodeWithText("58 albümün kapağı").assertExists()
        compose.onNodeWithTag("covers-clear").performClick()
        assertEquals(1, cleared)
        compose.onNodeWithText("Henüz yok").assertExists()
        compose.onNodeWithTag("covers-clear").assertIsNotEnabled()
    }
}
