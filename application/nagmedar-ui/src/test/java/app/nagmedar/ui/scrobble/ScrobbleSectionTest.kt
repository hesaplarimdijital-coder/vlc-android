package app.nagmedar.ui.scrobble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** S-1008: Settings › Scrobble: the accounts, the switches, the queue, signing in with the form of each service. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h1400dp-mdpi-qwerty")
class ScrobbleSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = ScrobbleActions(
        setEnabled = { id, on -> calls += "${id.key} on=$on" },
        openSignIn = { calls += "open ${it.key}" },
        closeSignIn = { calls += "close" },
        signIn = { id, input -> calls += "sign in ${id.key} ${input.name}/${input.secret}" },
        forget = { calls += "forget ${it.key}" },
        sendNow = { calls += "send now" },
        openLink = { calls += "link $it" },
    )

    private val lastFmOut = ScrobbleRow(ScrobbleServiceId.LastFm, available = true, account = null, enabled = false, waiting = 0, held = false)
    private val brainzIn = ScrobbleRow(ScrobbleServiceId.ListenBrainz, available = true, account = "ada_karan", enabled = true, waiting = 3, held = false)

    private fun render(rows: List<ScrobbleRow>, form: SignInState = SignInState.Closed) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ScrobbleContent(rows, form, actions)
            }
        }
    }

    @Test
    fun anAccountSignedInShowsItsNameTheQueueAndTheButtons() {
        render(listOf(lastFmOut, brainzIn))
        compose.onNodeWithText("ada_karan olarak giriş yapıldı").assertExists()
        compose.onNodeWithText("Giriş yapılmadı").assertExists()
        compose.onNodeWithTag("scrobble-listenbrainz-waiting").assertExists()
        compose.onNodeWithText("3 scrobble gönderilmeyi bekliyor").assertExists()
        compose.onNodeWithTag("scrobble-listenbrainz-switch").assertIsOn().assertIsEnabled()
        // nothing to switch on without an account
        compose.onNodeWithTag("scrobble-lastfm-switch").assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("scrobble-listenbrainz-send-now").assertIsEnabled().performClick()
        compose.onNodeWithTag("scrobble-listenbrainz-forget").performClick()
        compose.onNodeWithTag("scrobble-lastfm-sign-in").performClick()
        compose.onNodeWithTag("scrobble-listenbrainz-switch").performClick()
        assertEquals(listOf("send now", "forget listenbrainz", "open lastfm", "listenbrainz on=false"), calls)
    }

    @Test
    fun nothingWaitingMeansNothingToSend() {
        render(listOf(brainzIn.copy(waiting = 0)))
        compose.onNodeWithText("Gönderilmeyi bekleyen yok").assertExists()
        compose.onNodeWithTag("scrobble-listenbrainz-send-now").assertIsNotEnabled()
    }

    @Test
    fun aBuildWithoutLastFmKeysSaysSoAndCannotSignIn() {
        render(listOf(lastFmOut.copy(available = false)))
        compose.onNodeWithTag("scrobble-lastfm-no-key").assertExists()
        compose.onNodeWithText("Bu sürümde Last.fm anahtarı yok, bu yüzden kullanılamaz.").assertExists()
        compose.onNodeWithTag("scrobble-lastfm-sign-in").assertIsNotEnabled()
        compose.onNodeWithTag("scrobble-lastfm-switch").assertIsNotEnabled()
    }

    @Test
    fun aRefusedAccountSaysWhyItsQueueWaitsAndOffersASignInAgain() {
        render(listOf(brainzIn.copy(held = true)))
        compose.onNodeWithTag("scrobble-listenbrainz-held").assertExists()
        compose.onNodeWithTag("scrobble-listenbrainz-send-now").assertIsNotEnabled()
        compose.onNodeWithTag("scrobble-listenbrainz-sign-in").performClick()
        assertEquals(listOf("open listenbrainz"), calls)
    }

    @Test
    fun lastFmSignsInWithANameAndAPasswordThatStaysUnreadable() {
        render(listOf(lastFmOut), SignInState.Open(ScrobbleServiceId.LastFm))
        compose.onNodeWithTag("scrobble-lastfm-submit").assertIsNotEnabled()
        compose.onNodeWithTag("scrobble-lastfm-user").performTextInput("ada_karan")
        compose.onNodeWithTag("scrobble-lastfm-submit").assertIsNotEnabled()
        compose.onNodeWithTag("scrobble-lastfm-password").performTextInput("parola")
        compose.onNodeWithTag("scrobble-lastfm-submit").assertIsEnabled().performClick()
        compose.onNodeWithTag("scrobble-lastfm-cancel").performClick()
        assertEquals(listOf("sign in lastfm ada_karan/parola", "close"), calls)
    }

    @Test
    fun listenBrainzSignsInWithAPastedTokenAndLinksToItsSettingsPage() {
        render(listOf(lastFmOut.copy(id = ScrobbleServiceId.ListenBrainz)), SignInState.Open(ScrobbleServiceId.ListenBrainz))
        compose.onNodeWithTag("scrobble-listenbrainz-token").performTextInput("3f0e1c6a-0000-4d5a-9a58-6b1b7a3c2f10")
        compose.onNodeWithTag("scrobble-listenbrainz-open-settings").performClick()
        compose.onNodeWithTag("scrobble-listenbrainz-submit").performClick()
        assertEquals(listOf("link https://listenbrainz.org/settings/", "sign in listenbrainz /3f0e1c6a-0000-4d5a-9a58-6b1b7a3c2f10"), calls)
    }

    @Test
    fun whatWentWrongIsSaidUnderTheForm() {
        var form by mutableStateOf<SignInState>(SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Refused))
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ScrobbleContent(listOf(lastFmOut), form, actions)
            }
        }
        compose.onNodeWithText("Last.fm bu kullanıcı adını ve parolayı kabul etmedi.").assertExists()
        form = SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Offline)
        compose.onNodeWithText("Bağlantı yok. Ağı denetleyip yeniden deneyin.").assertExists()
        form = SignInState.Open(ScrobbleServiceId.LastFm, SignInProblem.Server, 11)
        compose.onNodeWithText("Last.fm hata ile yanıt verdi (11). Daha sonra yeniden deneyin.").assertExists()
        form = SignInState.Working(ScrobbleServiceId.LastFm)
        compose.onNodeWithTag("scrobble-lastfm-working").assertExists()
        compose.onNodeWithTag("scrobble-lastfm-submit").assertIsNotEnabled()
        form = SignInState.Closed
        compose.onNodeWithTag("scrobble-lastfm-form").assertDoesNotExist()
    }
}
