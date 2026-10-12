package app.nagmedar.ui.player.subtitles

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-701: the subtitles found beside the film are offered by language, the forced and the SDH ones
 * marked; one chosen, the card closes; a drop on the video says what it does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SubtitleViewsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSubtitlesFoundAreChosenByLanguage() {
        val chosen = mutableListOf<String>()
        var dismissed = false
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitleChooser(sampleFound, onChoose = { chosen += it.name }, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("Klasörde 4 altyazı dosyası var").assertExists()
        compose.onNodeWithText("Hangisi gösterilsin?").assertExists()
        assertEquals(4, compose.onAllNodesWithTag("subtitle-candidate").fetchSemanticsNodes().size)
        compose.onAllNodesWithText("Türkçe").assertCountEquals(2)
        compose.onNodeWithText("İngilizce").assertExists()
        compose.onNodeWithText("Dil belirsiz").assertExists()
        compose.onNodeWithText("zorunlu").assertExists()
        compose.onNodeWithText("işitme engelliler için").assertExists()
        compose.onNodeWithText("Kuzey Işığı.en.SDH.srt").performClick()
        compose.onNodeWithContentDescription("Kapat").performClick()
        assertEquals(listOf("Kuzey Işığı.en.SDH.srt"), chosen)
        assertTrue(dismissed)
    }

    @Test
    fun nothingFoundShowsNothing() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitleChooser(emptyList(), onChoose = {}, onDismiss = {})
            }
        }
        compose.onNodeWithTag("subtitle-chooser").assertDoesNotExist()
    }

    @Test
    fun aDropOnTheVideoSaysWhatItDoes() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitleDropOverlay()
            }
        }
        compose.onNodeWithText("Altyazıyı bırakın").assertExists()
        compose.onNodeWithText("Hemen yüklenir ve bu filmle kalır; buraya bırakılan bir video ise oynatılır.").assertExists()
    }
}
