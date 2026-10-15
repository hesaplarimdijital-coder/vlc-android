package app.nagmedar.ui.player.subtitles.providers

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.OnlineSearchState
import app.nagmedar.ui.player.subtitles.OnlineSubtitlesSurface
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
 * S-707: Settings › Subtitles as a person uses it (the switch, the two buttons that move a
 * provider, the folder buttons of the local one), the choice kept in the preferences, and the
 * search window's line for each provider when there are several.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class ProvidersUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val full = ProviderCapabilities(searchByHash = true, searchByImdb = true, searchByName = true, searchBySeasonEpisode = true, searchByLanguage = true, needsKey = true, rateLimit = RateLimit(downloadsPerDay = 5))
    private val os = FakeProvider("opensubtitles", "OpenSubtitles", capabilities = full)
    private val local = FakeProvider("local-folder", "Local folder", capabilities = ProviderCapabilities(searchByName = true))
    private val registry = ProviderRegistry(listOf(os, local), MemoryProviderStore(), offByDefault = setOf("local-folder"))

    private val folder: String? = null
    private val log = mutableListOf<String>()

    private fun settings(rowsOf: () -> List<ProviderRow> = ::rows) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitleProvidersSection(
                    rows = rowsOf(),
                    folderName = folder,
                    onEnabled = { id, on -> registry.setEnabled(id, on); log += "$id=$on" },
                    onMove = { id, by -> registry.move(id, by); log += "$id$by" },
                    onChooseFolder = { log += "choose" },
                    onForgetFolder = { log += "forget" },
                )
            }
        }
    }

    private fun rows() = registry.ordered.map {
        ProviderRow(
            id = it.id,
            name = it.displayName,
            capabilities = it.capabilities,
            enabled = registry.isEnabled(it.id),
            needsFolder = it.id == "local-folder",
            folderChosen = folder != null && it.id == "local-folder",
            keyMissing = it.capabilities.needsKey && it.unavailable() != null,
        )
    }

    @Test
    fun aProviderIsTurnedOnAndOff() {
        settings()
        assertEquals(false, registry.isEnabled("local-folder"))
        compose.onNodeWithTag("provider-switch-local-folder").performClick()
        compose.waitForIdle()
        assertEquals(true, registry.isEnabled("local-folder"))
        assertEquals(listOf("local-folder=true"), log)
        compose.onNodeWithTag("provider-switch-opensubtitles").performClick()
        compose.waitForIdle()
        assertEquals(false, registry.isEnabled("opensubtitles"))
    }

    @Test
    fun theTwoButtonsMoveAProviderAndTheEndsStopThem() {
        settings()
        compose.onNodeWithTag("provider-up-opensubtitles").assertIsNotEnabled()
        compose.onNodeWithTag("provider-down-local-folder").assertIsNotEnabled()
        compose.onNodeWithTag("provider-down-opensubtitles").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf("local-folder", "opensubtitles"), registry.ordered.map { it.id })
        // the rows follow: the first one cannot go up any more, and it is the other now
        compose.onNodeWithTag("provider-up-local-folder").assertIsNotEnabled()
        compose.onNodeWithTag("provider-up-opensubtitles").assertIsEnabled()
    }

    @Test
    fun theFolderIsChosenAndForgotten() {
        var chosen by mutableStateOf<String?>(null)
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                SubtitleProvidersSection(
                    rows = rows().map { it.copy(folderChosen = it.needsFolder && chosen != null) },
                    folderName = chosen,
                    onEnabled = { _, _ -> },
                    onMove = { _, _ -> },
                    onChooseFolder = { log += "choose"; chosen = "Download/Subtitles" },
                    onForgetFolder = { log += "forget"; chosen = null },
                )
            }
        }
        compose.onAllNodesWithTag("provider-folder-forget").assertCountEquals(0)
        compose.onNodeWithTag("provider-folder-choose").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("provider-folder-forget").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("provider-folder-forget").assertCountEquals(0)
        assertEquals(listOf("choose", "forget"), log)
    }

    @Test
    fun aProviderWithoutAKeyIsToldSo() {
        os.problem = OnlineProblem.NoKey
        settings()
        compose.onNodeWithTag("provider-nokey-opensubtitles").assertExists()
        compose.onAllNodesWithTag("provider-nokey-local-folder").assertCountEquals(0)
    }

    @Test
    fun theChoiceIsKeptInThePreferences() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("providers-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PrefsProviderSettingsStore(prefs)
        assertEquals(ProviderSettings(), store.load())
        val saved = ProviderSettings(order = listOf("local-folder", "opensubtitles"), off = setOf("opensubtitles"), on = setOf("local-folder"))
        store.save(saved)
        assertEquals(saved, PrefsProviderSettingsStore(prefs).load())
        val again = ProviderRegistry(listOf(os, local), PrefsProviderSettingsStore(prefs), setOf("local-folder"))
        assertEquals(listOf("local-folder"), again.enabled().map { it.id })
        assertEquals(listOf("local-folder", "opensubtitles"), again.ordered.map { it.id })
    }

    @Test
    fun theFolderIsKeptInThePreferences() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("folder-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = SubtitleFolderStore(prefs)
        assertNull(store.get())
        store.set("content://tree/primary%3ADownload%2FSubtitles")
        assertEquals("content://tree/primary%3ADownload%2FSubtitles", SubtitleFolderStore(prefs).get())
        assertEquals("Download/Subtitles", folderLabel("content://tree/primary%3ADownload%2FSubtitles"))
        store.set(null)
        assertNull(store.get())
    }

    private fun window(state: OnlineSearchState) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.fillMaxWidth()) {
                    OnlineSubtitlesSurface(state, OnlineQuota(4, 5, null), 0, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }

    private val found = listOf(
        result("opensubtitles", "11", language = "tr", hashMatch = true, providerName = "OpenSubtitles").copy(alsoFrom = listOf("Local folder")),
        result("local-folder", "z", release = "Northern.Light", language = "en", providerName = "Local folder"),
    )

    @Test
    fun eachProviderHasALineWhenSeveralWereAsked() {
        window(
            OnlineSearchState(
                "Kuzey Işığı", listOf("tr"), listOf("tr", "en"),
                results = found,
                reports = listOf(ProviderReport("opensubtitles", "OpenSubtitles", 1), ProviderReport("local-folder", "Local folder", 0, OnlineProblem.NoFolder)),
            ),
        )
        compose.onNodeWithTag("online-report-opensubtitles").assertExists()
        compose.onNodeWithTag("online-report-local-folder").assertExists()
        // a result says where it came from
        compose.onNodeWithTag("online-source-opensubtitles/11", useUnmergedNode = true).assertExists()
        compose.onNodeWithTag("online-source-local-folder/z", useUnmergedNode = true).assertExists()
    }

    @Test
    fun oneProviderHasNoLineOfItsOwn() {
        window(OnlineSearchState("Kuzey Işığı", listOf("tr"), listOf("tr", "en"), results = found.take(1), reports = listOf(ProviderReport("opensubtitles", "OpenSubtitles", 1))))
        compose.onAllNodesWithTag("online-reports").assertCountEquals(0)
    }
}
