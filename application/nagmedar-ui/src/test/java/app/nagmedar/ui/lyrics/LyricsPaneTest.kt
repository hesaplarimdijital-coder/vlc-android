package app.nagmedar.ui.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

/**
 * S-1004: the line sung lit as the song goes, a line clicked goes there, the delay in tenths of a
 * second moves the lines; plain lyrics and none said so.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi-qwerty")
class LyricsPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private var time by mutableLongStateOf(18_000L)
    private var load: LyricsLoad by mutableStateOf(LyricsLoad.Found(LyricsSamples.track, LyricsSamples.synced))
    private val seeks = mutableListOf<Long>()
    private var lookedAgain = 0

    private fun render() {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(400.dp).height(700.dp)) {
                    LyricsPane(
                        load = load,
                        timeMs = { time },
                        onSeek = { seeks += it },
                        onDelay = { ms -> (load as? LyricsLoad.Found)?.let { load = it.copy(lyrics = it.lyrics.copy(offsetMs = ms)) } },
                        onLookAgain = { lookedAgain++ },
                    )
                }
            }
        }
    }

    @Test
    fun theLineSungIsLitAsTheSongGoes() {
        render()
        compose.onNodeWithTag("lyrics-line-1").assertIsSelected()
        compose.onNodeWithTag("lyrics-line-0").assertIsNotSelected()
        time = 29_000
        compose.onNodeWithTag("lyrics-line-3").assertIsSelected()
        compose.onNodeWithTag("lyrics-line-1").assertIsNotSelected()
        // a line clicked: the song goes to it
        compose.onNodeWithTag("lyrics-line-5").performClick()
        assertEquals(listOf(38_000L), seeks)
    }

    @Test
    fun theDelayInTenths() {
        render()
        compose.onNodeWithTag("lyrics-delay").assertTextEquals("0 sn")
        repeat(3) { compose.onNodeWithTag("lyrics-later").performClick() }
        compose.onNodeWithTag("lyrics-delay").assertTextEquals("+0,3 sn")
        // shown 300 ms later: at 17,7 s the first line is still sung
        time = 17_700
        compose.onNodeWithTag("lyrics-line-0").assertIsSelected()
        // a click goes where the line now is sung
        compose.onNodeWithTag("lyrics-line-2").performClick()
        assertEquals(listOf(23_100L), seeks)
        compose.onNodeWithTag("lyrics-earlier").performClick()
        compose.onNodeWithTag("lyrics-delay").assertTextEquals("+0,2 sn")
        compose.onNodeWithTag("lyrics-delay-reset").performClick()
        compose.onNodeWithTag("lyrics-delay").assertTextEquals("0 sn")
        compose.onNodeWithText("LRCLIB'den").assertExists()
    }

    @Test
    fun plainLyricsAndNone() {
        load = LyricsLoad.Found(LyricsSamples.track, LyricsSamples.plain)
        render()
        compose.onNodeWithText("Düz söz: satırlar zamanlanmamış").assertExists()
        compose.onNodeWithText("Dosyanın etiketlerinden").assertExists()
        compose.onNodeWithTag("lyrics-later").assertDoesNotExist()
        load = LyricsLoad.Missing(LyricsSamples.track)
        compose.onNodeWithTag("lyrics-missing").assertExists()
        compose.onNodeWithText("Yeniden ara").performClick()
        assertEquals(1, lookedAgain)
    }
}
