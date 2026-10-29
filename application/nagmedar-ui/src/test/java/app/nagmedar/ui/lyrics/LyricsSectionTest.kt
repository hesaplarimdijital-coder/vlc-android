package app.nagmedar.ui.lyrics

import androidx.compose.ui.test.assertIsEnabled
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

/** S-1003: Settings › Lyrics: LRCLIB turned off and on, what is kept for offline use counted and cleared. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class LyricsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val turned = mutableListOf<Boolean>()
    private var cleared = 0

    private fun render(kept: Int) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                LyricsSection(online = true, kept = kept, onOnlineChange = { turned += it }, onClear = { cleared++ })
            }
        }
    }

    @Test
    fun lrclibTurnedOffAndOn() {
        render(kept = 0)
        compose.onNodeWithText("Çevrimiçi sözler (LRCLIB)").assertExists()
        compose.onNodeWithTag("lyrics-online").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Kapalı").assertExists()
        compose.onNodeWithTag("lyrics-online").performClick().assertIsOn()
        assertEquals(listOf(false, true), turned)
    }

    @Test
    fun keptAndCleared() {
        render(kept = 37)
        compose.onNodeWithText("37 parçanın sözleri").assertExists()
        compose.onNodeWithTag("lyrics-clear").assertIsEnabled().performClick()
        assertEquals(1, cleared)
        compose.onNodeWithText("Henüz yok").assertExists()
        compose.onNodeWithTag("lyrics-clear").assertIsNotEnabled()
    }
}
