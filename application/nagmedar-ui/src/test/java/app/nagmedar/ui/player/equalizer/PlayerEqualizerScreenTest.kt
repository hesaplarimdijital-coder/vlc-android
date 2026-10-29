package app.nagmedar.ui.player.equalizer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import app.nagmedar.ui.R
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player's equaliser window edits VLC's equaliser in Nağmedar's design: a preset is copied to
 * be changed, a band moves with the pointer, the screen reader and the arrow keys, each change
 * heard and kept; a film with its own equaliser from the effects tab says so, with the way back.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
class PlayerEqualizerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val store = FakeEqualizerStore(currentId = ROCK.id, on = true)
    private val target = FakeEqualizerTarget()
    private lateinit var controller: PlayerEqualizerController

    private fun show() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        controller = PlayerEqualizerController(store, target, CoroutineScope(Dispatchers.Unconfined)) { eq ->
            res.getString(R.string.nag_peq_copy_name, equalizerName(res, eq))
        }
        controller.load()
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                PlayerEqualizerScreen(controller, target.filmChoice)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPresetIsCopiedThenItsBandsMove() {
        show()
        compose.onNodeWithText("VLC'nin ön ayarları olduğu gibi kalır: birini değiştirmek için kopyasını oluşturun.").assertExists()
        compose.onNode(hasText("Kopyasını oluştur") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals("Rock kopyası", controller.state.current?.name)
        compose.onNode(hasContentDescription("60 Hz bandı")).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.waitForIdle()
        assertEquals(10f, controller.state.current?.bandsDb?.first())
        assertEquals(controller.state.current, target.heard.last())
        assertTrue("kept where VLC keeps it", store.writes.last() == "save:Rock kopyası")
    }

    @Test
    fun aBandTakesTheArrowKeys() {
        show()
        compose.onNode(hasText("Kopyasını oluştur") and hasClickAction()).performClick()
        compose.waitForIdle()
        val band = compose.onNode(hasContentDescription("1 kHz bandı"))
        band.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        band.performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        // Rock's 1 kHz band is at −3,2 dB: up to the next half decibel
        assertEquals(-2.5f, controller.state.current!!.bandsDb[4], 0.01f)
    }

    @Test
    fun offTheSoundPlaysAsItIs() {
        store.on = false
        show()
        compose.onNodeWithText("Ekolayzer kapalı: ses olduğu gibi çalıyor.").assertExists()
        compose.onNode(hasText("Ekolayzer açık") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue(controller.state.enabled)
        assertEquals(listOf<VlcEqualizer?>(ROCK), target.heard)
    }

    @Test
    fun aFilmWithItsOwnEqualizerSaysSoWithTheWayBack() {
        target.filmChoice = "rock"
        show()
        compose.onNodeWithText("Efektler sekmesindeki kendi ekolayzeriyle çalıyor: Rock. Buradaki ayar, oynatıcının ayarını izleyen filmlerde duyulur.").assertExists()
        compose.onNode(hasText("Bu filmde oynatıcının ayarını kullan") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertNull(target.filmChoice)
        assertEquals(listOf<VlcEqualizer?>(ROCK), target.heard)
    }
}
