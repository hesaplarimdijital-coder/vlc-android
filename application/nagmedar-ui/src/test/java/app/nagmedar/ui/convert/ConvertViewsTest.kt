package app.nagmedar.ui.convert

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-1303: the convert window starts only with a media and a profile this build can encode; the
 * profile editor keeps to the rules as the container changes and saves what it shows; the HLS
 * sheet starts, shows the address and stops.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class ConvertViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val film = ConvertMedia(Uri.parse("content://docs/kuzey.mkv"), "Kuzey Işığı.mkv", 4_000_000_000)

    @Test
    fun theConvertWindowNeedsAMediaAndAProfileItCanEncode() {
        var media by mutableStateOf<ConvertMedia?>(null)
        var selected by mutableStateOf(ConvertProfiles.presets.single { it.id == -2L })
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                ConvertSurface(
                    media = media,
                    profiles = ConvertProfiles.presets,
                    selected = selected,
                    onSelect = { selected = it },
                    onPickMedia = { calls += "pick" },
                    onNewProfile = { calls += "new" },
                    onStart = { calls += "start" },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
        compose.onNodeWithTag("convert-start").assertIsNotEnabled()
        compose.onNodeWithTag("convert-pick-media").performClick()
        media = film
        compose.waitForIdle()
        compose.onNodeWithText("Kuzey Işığı.mkv").assertExists()
        compose.onNodeWithTag("convert-start").assertIsEnabled().performClick()
        // H.265 is not in this build: said, and the start waits
        selected = ConvertProfiles.presets.single { it.id == -3L }
        compose.waitForIdle()
        compose.onNodeWithText("libVLC'nin bu derlemesinde bunun kodlayıcısı yok (H.265)").assertExists()
        compose.onNodeWithTag("convert-start").assertIsNotEnabled()
        // the profiles' menu: a preset chosen
        compose.onNodeWithTag("convert-profile-menu").performClick()
        compose.onNode(hasText("MP3 · 320 kbps") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(-6L, selected.id)
        assertEquals(listOf("pick", "start"), calls)
    }

    @Test
    fun theEditorKeepsToTheRules() {
        var saved: ConvertProfile? = null
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                ProfileEditorSurface(
                    initial = ConvertProfiles.presets.single { it.id == -1L }.copy(id = 0, name = "Arşiv", nameRes = null),
                    problemsOf = { ConvertProfiles.validate(it, listOf("Telefon")) },
                    onSave = { saved = it },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
        // a name already used: said, and Save waits
        compose.onNodeWithTag("convert-profile-name").performTextReplacement("telefon")
        compose.onNodeWithText("Bu adda bir profil zaten var").assertExists()
        compose.onNodeWithTag("convert-profile-save").assertIsNotEnabled()
        compose.onNodeWithTag("convert-profile-name").performTextReplacement("Kayıpsız ses")
        // FLAC: the video goes, the audio becomes FLAC, no bitrate to set
        compose.onNodeWithTag("convert-container").performClick()
        compose.onNode(hasText("FLAC") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("convert-video-codec").assertDoesNotExist()
        compose.onNodeWithTag("convert-audio-kbps").assertDoesNotExist()
        compose.onNodeWithTag("convert-profile-save").assertIsEnabled().performClick()
        val profile = saved!!
        assertEquals("Kayıpsız ses", profile.name)
        assertEquals(ConvertContainer.Flac, profile.container)
        assertNull(profile.video)
        assertEquals(AudioCodec.Flac, profile.audio!!.codec)
        assertEquals(SubtitleMode.None, profile.subtitles)
    }

    @Test
    fun theHlsSheetStartsShowsTheAddressAndStops() {
        var state by mutableStateOf<HlsState>(HlsState.Idle)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                HlsSurface(
                    media = film,
                    state = state,
                    quality = HlsQuality.P720,
                    onQuality = { calls += "quality:$it" },
                    onPickMedia = { calls += "pick" },
                    onStart = { calls += "start" },
                    onStop = { calls += "stop" },
                    onCopy = { calls += "copy:$it" },
                    onDismiss = { calls += "close" },
                )
            }
        }
        compose.onNodeWithTag("hls-quality").performClick()
        compose.onNode(hasText("480p · 1,2 Mbps") and hasClickAction()).performClick()
        compose.onNodeWithTag("hls-start").performClick()
        state = HlsState.Live("Kuzey Işığı", HlsQuality.P720, "http://192.168.1.20:41234/stream.m3u8", 0)
        compose.waitForIdle()
        compose.onNodeWithTag("convert-hls-url").assertExists()
        compose.onNodeWithTag("convert-hls-qr").assertExists()
        compose.onNodeWithText("Adresi kopyala").performClick()
        compose.onNodeWithTag("convert-hls-stop").performClick()
        state = HlsState.Failed(HlsFailure.NoLan)
        compose.waitForIdle()
        compose.onNodeWithText("Cihaz yerel bir ağda değil. Yayın için Wi-Fi'ye ya da Ethernet'e bağlanın.").assertExists()
        assertEquals(listOf("quality:P480", "start", "copy:http://192.168.1.20:41234/stream.m3u8", "stop"), calls)
    }
}
